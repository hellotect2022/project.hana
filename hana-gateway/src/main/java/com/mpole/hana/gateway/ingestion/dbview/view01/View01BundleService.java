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
            XnPointData first = points.getFirst();
            String deviceKey = deriveDeviceKey(first.pointkey(), first.tagName());
            DeviceBundle result = DeviceBundle.builder()
                    .deviceKey(deviceKey)
                    .deviceId(entry.getKey())
                    .timestamp(first.updateDatetime())
                    .points(points)
                    .build();
            log.debug(" - deviceId: {}, points: {} ", result.deviceId(), points);
            log.debug("================================================");
            return result;
        });
    }

    /**
     * {@code point_key} 에서 {@code device_key} 를 되돌린다.
     *
     * <p>{@code point_key} 는 항상 <b>{@code {device_key}_{tag_name}}</b> 로 만들어지므로,
     * <b>뒤에서 태그명 길이 + 1 만큼</b> 떼면 device_key 가 정확히 남는다.</p>
     *
     * <pre>
     *   ELEC_EE_MRS_F011_M165_pos   tag=pos   →  ELEC_EE_MRS_F011_M165
     *   HSE_CLG_CT_CT-2_temp        tag=temp  →  HSE_CLG_CT_CT-2
     * </pre>
     *
     * <p><b>앞에서 세면 안 된다.</b> 예전 구현은 앞쪽 언더스코어 4개까지를 잘랐는데, 그건
     * "대_중_소_이름" 이 정확히 4토막이라는 가정이다. 실제로는 장비명에 {@code _} 가 들어가고
     * (전체 장비의 21%) 카테고리 코드에도 들어간다({@code ELV_GR}, {@code MRS_CONTROL}).
     * 그래서 {@code ELEC_EE_MRS_F011_M165} 가 {@code ELEC_EE_MRS_F011} 로 잘려
     * <b>서로 다른 장비가 같은 key 를 갖는</b> 상태가 되었다.</p>
     *
     * <p>태그명은 뒤쪽에 정확히 한 번만 붙으므로 이 방식은 {@code _} 개수와 무관하게 안전하다.</p>
     *
     * @return 유도한 device_key. 형식이 어긋나면 {@code point_key} 를 그대로 돌려준다
     *         (잘못 자르느니 원본을 두는 편이 추적에 낫다)
     */
    public static String deriveDeviceKey(String pointKey, String tagName) {
        if (pointKey == null || pointKey.isEmpty() || tagName == null || tagName.isEmpty()) {
            return pointKey;
        }
        String suffix = "_" + tagName;
        if (!pointKey.endsWith(suffix) || pointKey.length() == suffix.length()) {
            // 규칙에서 벗어난 키 — 자르지 않고 원본 유지
            log.warn("[Bundle] point_key 가 '_{}' 로 끝나지 않는다: {}", tagName, pointKey);
            return pointKey;
        }
        return pointKey.substring(0, pointKey.length() - suffix.length());
    }
}
