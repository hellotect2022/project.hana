package com.mpole.hana.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.event.config.ReactiveEventQueue;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 디바이스 실시간 값 <b>이력 적재(append-only)</b> 파이프라인.
 *
 * <p>현재값 upsert({@link SnapshotService})와 별개로, 멀티캐스트 raw flux 를 <b>독립 구독</b>하고
 * 전용 스케줄러("value-history-thread")에서 동작한다 → 현재값 파이프라인에 회귀 영향 없음.
 * device 별 bundle 1행을 {@code tbl_device_value_history}(TimescaleDB 하이퍼테이블)에 계속 쌓는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceValueHistoryService {

    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;
    private final ReactiveEventQueue reactiveEventQueue;

    @PostConstruct
    public void startHistoryPipeline() {
        log.info("[DeviceValueHistory] 실시간 값 이력 적재 파이프라인 시작");
        reactiveEventQueue.getDeviceRawFlux()
                .publishOn(Schedulers.newParallel("value-history-thread"))
                .flatMap(bundles -> appendBulk(bundles)
                        .onErrorResume(e -> {
                            log.error("[DeviceValueHistory] 적재 에러(해당 배치 skip): {}", e.getMessage());
                            return Mono.empty();
                        }))
                //.subscribe()
        ;
    }

    /**
     * 배치의 모든 bundle 을 device 별 1행으로 append INSERT. created_at = 수신 시각.
     *
     * <p>gateway 가 값 변화와 무관하게 매번 발행하므로 <b>동일 (device_id, created_at)</b> 이 재전송될 수 있다.
     * 복합 PK (device_id, created_at) 충돌은 다음 2단계로 처리한다:</p>
     * <ol>
     *   <li>배치 <b>내부</b> 중복(같은 (deviceId, timestamp) 2건 이상)은 첫 건만 남기고 선-제거</li>
     *   <li>배치 <b>외부</b>(기존 적재분)와의 충돌은 {@code ON CONFLICT DO NOTHING} 으로 해당 행만 skip</li>
     * </ol>
     * <p>→ 중복 아닌 행은 정상 insert 되고, 배치 전체가 실패하지 않는다. skip 개수는 로그로 남긴다.</p>
     */
    public Mono<Void> appendBulk(List<DeviceBundle> bundles) {
        if (bundles == null || bundles.isEmpty()) {
            return Mono.empty();
        }

        // 1) 배치 내부 (deviceId, timestamp) 중복 제거 — 첫 건만 유지(입력 순서 보존)
        LinkedHashMap<String, DeviceBundle> unique = new LinkedHashMap<>();
        for (DeviceBundle b : bundles) {
            unique.putIfAbsent(b.deviceId() + "|" + b.timestamp(), b);
        }
        int intraDup = bundles.size() - unique.size();
        List<DeviceBundle> deduped = new ArrayList<>(unique.values());

        String valuesClause = deduped.stream()
                .map(b -> {
                    String jsonPoints;
                    try {
                        jsonPoints = objectMapper.writeValueAsString(b.points());
                    } catch (Exception e) {
                        throw new RuntimeException("points 직렬화 실패", e);
                    }
                    return String.format("(%d, '%s', '%s', '%s'::jsonb)",
                            b.deviceId(),
                            esc(b.deviceKey()),
                            b.timestamp(),
                            jsonPoints.replace("'", "''"));
                })
                .collect(Collectors.joining(","));

        // 2) 기존 적재분과 (device_id, created_at) 충돌 시 해당 행만 skip
        String sql = "INSERT INTO tbl_device_value_history (device_id, device_key, created_at, points) VALUES "
                + valuesClause
                + " ON CONFLICT (device_id, created_at) DO NOTHING";

        int candidate = deduped.size();
        long t = System.currentTimeMillis();
        return postgresDatabaseClient.sql(sql)
                .fetch()
                .rowsUpdated()
                .doOnNext(inserted -> {
                    long crossDup = candidate - inserted;   // 기존 적재분과의 중복(ON CONFLICT skip)
                    long totalSkip = intraDup + crossDup;
                    if (totalSkip > 0) {
                        log.info("[DeviceValueHistory] {}행 적재, 중복시각 {}행 skip(배치내 {}, 기존중복 {}) {}ms",
                                inserted, totalSkip, intraDup, crossDup, System.currentTimeMillis() - t);
                    } else {
                        log.debug("[DeviceValueHistory] {}행 적재 {}ms", inserted, System.currentTimeMillis() - t);
                    }
                })
                .then()
                .doOnError(e -> log.error("[DeviceValueHistory] append INSERT DB 에러: {}", e.getMessage()));
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
