package com.mpole.hana.gateway.init;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.constant.PointType;
import com.mpole.hana.common.dto.DeviceRawPointData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InitDevicePointDataService implements SmartLifecycle {
    private boolean running = false;
    // 1:N 팬아웃 — 하나의 외부신호(ref_device_code:ref_point_code)를 여러 device_point 가 공유할 수 있어
    // 값이 단건이 아니라 List. (기존 toMap last-wins 는 중복 point 를 덮어써 유실시켰음)
    private final Map<String, List<DeviceRawPointData>> pointMap = new ConcurrentHashMap<>();
    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;

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
        }).then();
    }

    private Mono<List<DeviceRawPointData>> loadPoints() {
        // [소스 8] tbl_device_point 기반 조회
//        return postgresDatabaseClient
//                .sql("""
//                    SELECT
//                        raw_point_id,
//                        mapped_device_id,
//                        device_code,
//                        point_code,
//                        candidate_device_name,
//                        candidate_point_name,
//                        schema_tag_name,
//                        object_type,
//                        alarm_yn
//                    FROM tbl_raw_point
//                    WHERE mapped_device_id IS NOT NULL
//                    """)
//                .map((row, meta) -> {
//                    return DeviceRawPointData.builder()
//                            .pointId(row.get("raw_point_id", Long.class))
//                            .deviceId(row.get("mapped_device_id", Long.class))
//                            .deviceCode(row.get("device_code", String.class))
//                            .pointCode(row.get("point_code", String.class))
//                            //.pointType(PointType.valueOf(row.get("object_type", String.class)))
//                            //.isAlarmSource(row.get("alarm_yn", String.class)) // SOP 트리거 필드
//                            .schemaTagName(row.get("schema_tag_name", String.class))
//                            .build();
//                })
//                .all()
//                .collectList();
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
        this.running = true;
    }

    @Override
    public void stop() {
        log.info("초기화 Stop: Device Points 캐싱 종료... {}개 로드", pointMap.size());
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }
}
