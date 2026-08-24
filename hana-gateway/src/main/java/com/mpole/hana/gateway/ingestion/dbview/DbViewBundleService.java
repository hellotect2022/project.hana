package com.mpole.hana.gateway.ingestion.dbview;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.gateway.init.InitDevicePointDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DbViewBundleService {
    private final InitDevicePointDataService initDevicePointDataService;

    public Flux<DeviceBundle> bundle(Flux<XnPointData> rawDataStream) {
        return rawDataStream
                .collectList()
                .flatMapMany(allData -> {
                    long start = System.currentTimeMillis();
                    Map<Long, List<XnPointData>> grouped = allData
                            .parallelStream()
                            .<XnPointData>mapMulti((raw, consumer) -> {
                                // 1:N 팬아웃 — 하나의 신호(deviceCode:pointCode)가 여러 device_point 로 방출.
                                for (var meta : initDevicePointDataService.getPoints(raw.deviceCode() + ":" + raw.pointCode())) {
                                    consumer.accept(
                                            raw.toBuilder()
                                                    .pointkey(meta.pointKey())
                                                    .deviceId(meta.deviceId())
                                                    .pointId(meta.pointId())
                                                    .tagName(meta.schemaTagName())
                                                    .unit(meta.unit())
                                                    .objectType(meta.pointType().name())
                                                    .build());
                                }
                            })
                            .collect(Collectors.groupingBy(XnPointData::deviceId));

//

//                    var result = grouped.entrySet().stream().map(entry -> {
//                        List<XnPointData> points = entry.getValue();
//                        points.sort(Comparator.comparing(XnPointData::updateDatetime).reversed());
//
//                        return DeviceBundle.builder()
//                                .deviceId(entry.getKey())
//                                .timestamp(points.getFirst().updateDatetime())
//                                .points(points)
//                                .build();
//                    }).toList();

                    log.info(" - 순수 번들링 갯수: {} 개", grouped.size());
                    log.debug(" - 순수 번들링 데이타: {} ", grouped);
                    log.info(" - 순수 번들링 가공 소요 시간: {}ms", System.currentTimeMillis() - start);
                    return Flux.fromIterable(grouped.entrySet()).map(entry -> {
                        List<XnPointData> points = entry.getValue();
                        points.sort(Comparator.comparing(XnPointData::updateDatetime).reversed());
                        String pk = points.getFirst().pointkey();
                        String deviceKey = cutByUnderscore(pk,4);
                        return DeviceBundle.builder()
                                .deviceKey(deviceKey)
                                .deviceId(entry.getKey())
                                .timestamp(points.getFirst().updateDatetime())
                                .points(points)
                                .build();
                    });
                });
    }

    public static String cutByUnderscore(String value, int underscoreCount) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        int pos = -1;

        for (int i = 0; i < underscoreCount; i++) {
            pos = value.indexOf('_', pos + 1);

            if (pos == -1) {
                return value;
            }
        }

        return value.substring(0, pos);
    }
}
