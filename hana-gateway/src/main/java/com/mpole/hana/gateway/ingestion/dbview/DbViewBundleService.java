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

                    Map<Long, List<XnPointData>> grouped = allData
                            .parallelStream()
                            .<XnPointData>mapMulti((raw, consumer) -> {
                                var meta = initDevicePointDataService.getPoint(raw.deviceCode() + ":" + raw.pointCode());
                                if (meta != null) {
                                    consumer.accept(raw.toBuilder()
                                            .deviceId(meta.deviceId())
                                            .pointId(meta.pointId())
                                            .tagName(meta.schemaTagName())
                                            .build());
                                }
                            })
                            .collect(Collectors.groupingBy(XnPointData::deviceId));

                    var result = grouped.entrySet().stream().map(entry -> {
                        List<XnPointData> points = entry.getValue();
                        points.sort(Comparator.comparing(XnPointData::updateDatetime).reversed());

                        return DeviceBundle.builder()
                                .deviceId(entry.getKey())
                                .timestamp(points.getFirst().updateDatetime())
                                .points(points)
                                .build();
                    }).toList();

                    return Flux.fromIterable(result);
                });
    }
}
