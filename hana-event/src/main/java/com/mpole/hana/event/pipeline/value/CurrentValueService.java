package com.mpole.hana.event.pipeline.value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.event.config.sink.ReactiveQueue;
import io.r2dbc.spi.Result;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentValueService {

    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;
    private final ReactiveQueue reactiveQueue;

    /** 병합 시험용 복사 테이블 — scripts/current-value/2026-10-26-current-value-merge-test.sql */
    static final String CURRENT_VALUE_TABLE = "tbl_device_current_value";
    static final String MERGE_TEST_TABLE = "tbl_device_current_value_merge_test";

    /**
     * 현재값 기록 방식 (2026-10-26, 태그 단위 병합 시험).
     * <pre>
     *   REPLACE  기존 — points 배열 통째로 교체 (기본)
     *   SHADOW   기존 그대로 + 같은 데이터를 병합 방식으로 복사 테이블에 따로 기록(별도 구독·별도 스레드라 기존 경로에 지연 없음)
     *   MERGE    병합 방식으로 실제 테이블 기록 — 들어온 태그만 교체하고 나머지 태그는 보존
     * </pre>
     */
    @Value("${hdt.current-value.write-mode:REPLACE}")
    private String writeMode;

    @PostConstruct
    public void startSnapshotPipeline() {
        String mode = writeMode == null ? "REPLACE" : writeMode.trim().toUpperCase();
        if (!mode.equals("REPLACE") && !mode.equals("SHADOW") && !mode.equals("MERGE")) {
            log.warn("[CurrentValue] 알 수 없는 write-mode={} — REPLACE 로 동작", writeMode);
            mode = "REPLACE";
        }
        boolean merge = mode.equals("MERGE");
        log.info("[CurrentValue] 현재값 기록 방식 write-mode={}", mode);

        reactiveQueue.getDeviceRawFlux()
                .publishOn(Schedulers.newParallel("snapshot-thread"))
                .concatMap(bundles->{
                    //log.info("snapshot 용으로 수신하는 flux 데이타 {}",bundles.size());
                    return (merge ? mergeBulk(bundles, CURRENT_VALUE_TABLE) : upsertBulk(bundles))
                            .doOnError(e -> log.error("[CurrentValue] 배치 스킵: {}", e.getMessage()))
                            .onErrorResume(e->Mono.empty());
                })
                .subscribe(null, e -> log.error("[CurrentValue] 스트림 치명적 종료", e))
        ;

        if (mode.equals("SHADOW")) {
            startShadowPipeline();
        }
    }

    /** SHADOW — 같은 raw 스트림을 독립 구독해 복사 테이블에 병합 기록. 실패해도 기존 경로와 무관 */
    private void startShadowPipeline() {
        reactiveQueue.getDeviceRawFlux()
                .publishOn(Schedulers.newSingle("current-value-shadow-thread"))
                .concatMap(bundles -> mergeBulk(bundles, MERGE_TEST_TABLE)
                        .doOnError(e -> log.error("[CurrentValue:SHADOW] 배치 스킵: {}", e.getMessage()))
                        .onErrorResume(e -> Mono.empty()))
                .subscribe(null, e -> log.error("[CurrentValue:SHADOW] 스트림 치명적 종료", e));
        log.info("[CurrentValue:SHADOW] 병합 시험 기록 시작 → {}", MERGE_TEST_TABLE);
    }

    /**
     * <b>태그 단위 병합</b> upsert (2026-10-26) — 들어온 묶음에 있는 태그만 기존 points 에서 빼고 새 값을 붙인다.
     *
     * <ul>
     *   <li>게이트웨이처럼 장비 전체 포인트를 보내면 기존 원소가 모두 빠지므로 결과는 {@link #upsertBulk} 와 같다.</li>
     *   <li>일부 태그만 보내면(물리버튼·부스 등) 나머지 태그 값이 보존된다.</li>
     *   <li>같은 배치에 같은 장비가 여러 번 오면 태그별로 합친다 — 묶음 시각 오름차순으로 덮어써 최신 값이 남는다
     *       (REPLACE 처럼 장비당 1건만 남기면 일부 묶음 두 개 중 하나의 태그가 사라진다).</li>
     *   <li>SQL 한 문장이라 원자적이다. {@code connection_state} 는 건드리지 않는다.</li>
     * </ul>
     */
    public Mono<Void> mergeBulk(List<DeviceBundle> bundles, String table) {
        if (bundles == null || bundles.isEmpty()) return Mono.empty();

        List<DeviceBundle> merged = mergeByDevice(bundles);
        String valuesClause = merged.stream()
                .map(bundle -> {
                    String jsonPoints;
                    try {
                        jsonPoints = objectMapper.writeValueAsString(bundle.points());
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    return String.format("(%s, %d, '%s', '%s', '%s'::jsonb)",
                            bundle.deviceKey() == null ? "NULL" : "'" + bundle.deviceKey().replace("'", "''") + "'",
                            bundle.deviceId(),
                            bundle.timestamp(),
                            bundle.timestamp(),
                            jsonPoints.replace("'", "''"));
                })
                .collect(Collectors.joining(","));

        String sql = String.format("""
            INSERT INTO %1$s AS t
                (device_key, device_id, created_at, updated_at, points)
            VALUES %2$s
            ON CONFLICT (device_id)
            DO UPDATE SET
                device_key = EXCLUDED.device_key,
                points = COALESCE(
                    (SELECT jsonb_agg(old.e ORDER BY old.ord)
                       FROM jsonb_array_elements(COALESCE(t.points, '[]'::jsonb)) WITH ORDINALITY AS old(e, ord)
                      WHERE NOT EXISTS (
                            SELECT 1 FROM jsonb_array_elements(EXCLUDED.points) AS n(e)
                             WHERE n.e->>'tagName' IS NOT DISTINCT FROM old.e->>'tagName')),
                    '[]'::jsonb
                ) || EXCLUDED.points,
                updated_at = EXCLUDED.updated_at
            """, table, valuesClause);

        long start = System.currentTimeMillis();
        int pointCount = merged.stream().mapToInt(b -> b.points() == null ? 0 : b.points().size()).sum();
        // upsertBulk 와 같은 이유로 statement 캐시를 타지 않게 inConnection 으로 실행
        return postgresDatabaseClient.inConnection(conn ->
                Flux.from(conn.createStatement(sql).execute()).flatMap(Result::getRowsUpdated)
                        .then()
                        .doOnSuccess(a -> log.info("[CurrentValue:MERGE] {} 장비 {}대 / 포인트 {}개 병합 {}ms",
                                table, merged.size(), pointCount, System.currentTimeMillis() - start)));
    }

    /** 같은 장비 묶음을 태그별로 합친다. 묶음 시각 오름차순으로 덮어써 최신 값이 남는다. 태그 순서는 처음 나온 순서 */
    static List<DeviceBundle> mergeByDevice(List<DeviceBundle> bundles) {
        List<DeviceBundle> ordered = new ArrayList<>(bundles);
        ordered.removeIf(b -> b == null || b.deviceId() == null);
        ordered.sort(Comparator.comparing(DeviceBundle::timestamp, Comparator.nullsFirst(Comparator.naturalOrder())));

        Map<Long, DeviceBundle> latestBundle = new LinkedHashMap<>();
        Map<Long, LinkedHashMap<String, XnPointData>> pointsByDevice = new LinkedHashMap<>();
        for (DeviceBundle b : ordered) {
            latestBundle.put(b.deviceId(), b);
            LinkedHashMap<String, XnPointData> tags = pointsByDevice.computeIfAbsent(b.deviceId(), k -> new LinkedHashMap<>());
            if (b.points() == null) continue;
            for (XnPointData p : b.points()) {
                if (p == null) continue;
                tags.put(String.valueOf(p.tagName()), p);   // tagName null 은 "null" 키 하나로 모인다(SQL 쪽도 IS NOT DISTINCT FROM)
            }
        }

        List<DeviceBundle> out = new ArrayList<>(latestBundle.size());
        latestBundle.forEach((deviceId, b) -> out.add(
                new DeviceBundle(deviceId, b.deviceKey(), b.timestamp(), List.copyOf(pointsByDevice.get(deviceId).values()))));
        return out;
    }

    public Mono<Void> upsertBulk(List<DeviceBundle> bundles) {
        if (bundles.isEmpty()) return Mono.empty();

        // [핵심] 1. 같은 배치 내에서 동일 device_id는 '가장 최신 것' 하나만 남깁니다.
        // 이렇게 하면 "cannot affect row a second time" 에러가 원천 차단됩니다.
        Collection<DeviceBundle> uniqueBundles = bundles.stream()
                .collect(Collectors.toMap(
                        DeviceBundle::deviceId, // Key
                        bundle -> bundle,          // Value
                        (existing, replacement) -> replacement // 중복 시 나중에 들어온 것 선택
                ))
                .values();

        String valuesClause = uniqueBundles.stream()
                .map(bundle -> {
                    String jsonPoints = null;
                    try {
                        jsonPoints = objectMapper.writeValueAsString(bundle.points());
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    // PostgreSQL에서 싱글 쿼테이션(')은 두 번 쓰기('')로 이스케이프 처리
                    return String.format("('%s', %d, '%s', '%s', '%s'::jsonb)",
                            bundle.deviceKey(),
                            bundle.deviceId(),
                            bundle.timestamp(), // OffsetDateTime은 포맷에 맞게 문자열화
                            bundle.timestamp(),
                            jsonPoints.replace("'", "''")
                    );
                })
                .filter(obj -> true)
                .collect(Collectors.joining(","));

        // 2. 전체 Bulk Upsert 쿼리 생성
        String sql = String.format("""
            INSERT INTO tbl_device_current_value
                (device_key, device_id, created_at, updated_at, points)
            VALUES %s
            ON CONFLICT (device_id)
            DO UPDATE SET
                device_key = EXCLUDED.device_key,
                points = EXCLUDED.points,
                updated_at = EXCLUDED.updated_at
            """, valuesClause);

        var time = System.currentTimeMillis();

        //sql 문자열이 bind 되어서 넘어가는개 아니라 문자열로 만들어서 넣기 때문에 매번 대량의 sql 문 객체가 캐싱되어서 oom 이발생함
        // 근본적인건 bind 구조로 변경을 하는것이 최고이나 임시로 캐싱이 안되고 매번 13.7mb 사이즈의 query 문을 만들어서 실행하도록 설정도 가능하다ㅏ.
        return postgresDatabaseClient.inConnection(conn->
            Flux.from(conn.createStatement(sql).execute()).flatMap(Result::getRowsUpdated)
                    .then()
                    .doOnSuccess(a->log.info("db insert 하는데 걸린시간 ㅎㅎ ㅜㅜ {}ms",System.currentTimeMillis()- time))
        )

//        return postgresDatabaseClient.sql(sql)
//                .then()
//                .doOnSuccess(a->log.info("db insert 하는데 걸린시간 ㅎㅎ ㅜㅜ {}ms",System.currentTimeMillis()- time))
                //.doOnError(e -> log.error("Bulk Upsert 실행 중 DB 에러: {}", e.getMessage()))
                ;
    }
}
