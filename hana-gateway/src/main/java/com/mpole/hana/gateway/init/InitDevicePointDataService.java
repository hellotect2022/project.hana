package com.mpole.hana.gateway.init;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.constant.PointType;
import com.mpole.hana.common.dto.DeviceRawPointData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InitDevicePointDataService implements SmartLifecycle {
    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(30);
    private boolean running = false;
    // 1:N 팬아웃 — 하나의 외부신호(ref_device_code:ref_point_code)를 여러 device_point 가 공유할 수 있어
    // 값이 단건이 아니라 List. (기존 toMap last-wins 는 중복 point 를 덮어써 유실시켰음)
    private final Map<String, List<DeviceRawPointData>> pointMap = new ConcurrentHashMap<>();
    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;
    private Disposable refreshSubscription;


    public List<DeviceRawPointData> getPoints(String key) {
        return pointMap.getOrDefault(key, List.of());
    }

    public Mono<Void> refreshCache() {
        return loadPoints().map(pointMetaData -> {
            Map<String, List<DeviceRawPointData>> newPointMap = pointMetaData.stream()
                    .collect(Collectors.groupingByConcurrent(
                            p -> p.deviceCode() + ":" + p.pointCode())); // 신호 키 → point 목록
            this.pointMap.clear();
            this.pointMap.putAll(newPointMap);
            return Mono.empty();
        })
        .doOnSuccess(list -> log.info("[Device Points] 초기화 완료: {}개 신호키", pointMap.size()))
        .then();
    }

    private Mono<List<DeviceRawPointData>> loadPoints() {
        return postgresDatabaseClient
                .sql("""
                    SELECT
                        point_key,
                        point_id,
                        device_id,
                        ref_device_code,
                        ref_point_code,
                        tag_name,
                        point_type,
                        point_display_name,
                        unit
                    FROM tbl_device_point
                    """)
                .map((row, meta) -> {



                    return DeviceRawPointData.builder()
                            .pointKey(row.get("point_key",String.class))
                            .pointId(row.get("point_id", Long.class))
                            .deviceId(row.get("device_id", Long.class))
                            .deviceCode(row.get("ref_device_code", String.class))
                            .pointCode(row.get("ref_point_code", String.class))
                            .schemaTagName(row.get("tag_name", String.class))
                            .pointDisplayName(row.get("point_display_name", String.class))
                            .unit(row.get("unit", String.class))
                            .pointType(PointType.valueOf(row.get("point_type", String.class)))
                            .build();
                })
                .all()
                .collectList();
    }


    @Override
    public void start() {
        log.info("초기화 Start : Device Points 캐싱 시작...");
        refreshCache().block();

        this.refreshSubscription=Flux.interval(REFRESH_INTERVAL, REFRESH_INTERVAL)
                .onBackpressureDrop()
                .concatMap(tick -> refreshCache()
                        .doOnError(e -> log.error("[Device Points ] 주기 리프레시 실패(이전 스냅샷 유지): {}", e.getMessage()))
                        .onErrorResume(e -> Mono.empty()))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        this.running = true;
    }

    @Override
    public void stop() {
        log.info("초기화 Stop: Device Points 캐싱 종료... {}개 로드", pointMap.size());
        if (refreshSubscription != null) refreshSubscription.dispose();  // 종료 후 계속 도는 것 방지
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }
}
