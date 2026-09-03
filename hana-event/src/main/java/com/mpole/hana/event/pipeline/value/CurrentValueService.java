package com.mpole.hana.event.pipeline.value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.event.config.sink.ReactiveQueue;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentValueService {

    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;
    private final ReactiveQueue reactiveQueue;

    @PostConstruct
    public void startSnapshotPipeline() {
        reactiveQueue.getDeviceRawFlux()
                .publishOn(Schedulers.newParallel("snapshot-thread"))
                .flatMap(bundles->{
                    log.info("snapshot 용으로 수신하는 flux 데이타 {}",bundles.size());
                    return upsertBulk(bundles);
                })
                .subscribe()
        ;
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
        return postgresDatabaseClient.sql(sql)
                .then()
                .doOnSuccess(a->log.info("db insert 하는데 걸린시간 ㅎㅎ ㅜㅜ {}ms",System.currentTimeMillis()- time))
                .doOnError(e -> log.error("Bulk Upsert 실행 중 DB 에러: {}", e.getMessage()));
    }
}
