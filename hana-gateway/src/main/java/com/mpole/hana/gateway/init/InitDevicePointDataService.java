package com.mpole.hana.gateway.init;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final Map<String, DeviceRawPointData> pointMap = new ConcurrentHashMap<>();
    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;

    public DeviceRawPointData getPoint(String key) {
        return pointMap.get(key);
    }

    public Mono<Void> refreshCache() {
        return loadPoints().map(pointMetaData -> {
            Map<String, DeviceRawPointData> newPointMap = pointMetaData.stream()
                    .collect(Collectors.toConcurrentMap(p->p.deviceCode()+":"+p.pointCode(), p -> p));
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
//                    return PointMetaData.builder()
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
                        point_id,
                        device_id,
                        device_code,
                        point_code
                    FROM tbl_device_point
                    """)
                .map((row, meta) -> {



                    return DeviceRawPointData.builder()
                            .pointId(row.get("point_id", Long.class))
                            .deviceId(row.get("device_id", Long.class))
                            .deviceCode(row.get("device_code", String.class))
                            .pointCode(row.get("point_code", String.class))
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
