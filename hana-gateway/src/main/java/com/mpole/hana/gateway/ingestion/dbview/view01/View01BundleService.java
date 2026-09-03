package com.mpole.hana.gateway.ingestion.dbview.view01;

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

/**
 * <b>외부 뷰 #01 번들링 — 외부 신호를 우리 장비 단위로 묶는다.</b>
 *
 * <p>외부 신호키({@code ref_device_code:ref_point_code})를 {@link InitDevicePointDataService} 캐시로
 * 우리 {@code tbl_device_point} 에 매핑한 뒤, {@code deviceId} 기준으로 {@link DeviceBundle} 을 만든다.</p>
 *
 * <p><b>1:N 팬아웃</b> — 하나의 외부 신호를 여러 device_point 가 공유할 수 있어(공유 센서 등)
 * 신호 1건이 여러 건으로 방출된다. 그래서 매핑 결과가 단건이 아니라 List 다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class View01BundleService {
    private final InitDevicePointDataService initDevicePointDataService;

    public Flux<DeviceBundle> bundle(List<XnPointData> allData) {

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

        //log.debug(" - 순수 번들링 데이타: {} ", grouped);
        log.info(" - 번들링 갯수: {} 개, 소요시간: {}ms", grouped.size(),System.currentTimeMillis() - start);
        return Flux.fromIterable(grouped.entrySet()).map(entry -> {
            List<XnPointData> points = entry.getValue();
            points.sort(Comparator.comparing(XnPointData::updateDatetime,Comparator.nullsLast(Comparator.reverseOrder())));
            String pk = points.getFirst().pointkey();
            String deviceKey = cutByUnderscore(pk, 4);
            DeviceBundle result = DeviceBundle.builder()
                    .deviceKey(deviceKey)
                    .deviceId(entry.getKey())
                    .timestamp(points.getFirst().updateDatetime())
                    .points(points)
                    .build();
            log.debug(" - deviceId: {}, points: {} ", result.deviceId(), points);
            log.debug("================================================");
            return DeviceBundle.builder()
                    .deviceKey(deviceKey)
                    .deviceId(entry.getKey())
                    .timestamp(points.getFirst().updateDatetime())
                    .points(points)
                    .build();
        });
    }

    /** point_key 에서 앞쪽 N 번째 언더스코어까지를 잘라 device_key 를 유도한다. */
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
