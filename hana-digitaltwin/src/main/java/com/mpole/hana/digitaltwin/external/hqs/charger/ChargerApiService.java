package com.mpole.hana.digitaltwin.external.hqs.charger;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePlacement;
import com.mpole.hana.digitaltwin.external.hqs.charger.dto.HqsChargerItem;
import com.mpole.hana.digitaltwin.external.hqs.charger.dto.HqsConnectorItem;
import com.mpole.hana.digitaltwin.external.hqs.charger.dto.HqsStationItem;
import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;
import com.mpole.hana.digitaltwin.location.repository.UnityLocZoneRepository;
import com.mpole.hana.digitaltwin.location.service.ZoneCascadeService;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HQS 전기차 충전기 조회.
 *
 * <pre>
 *   충전소(station) = 구역(zone)          ← 별도 테이블 없음. zone_code 가 stationId
 *   충전기(charger) = 장비(device)        ← 카테고리 DCC(급속) / ACC(완속)
 *   충전구(connector) = 상태 포인트 1개    ← 충전기당 1개로 취급
 * </pre>
 *
 * <p><b>충전기 1대 = 충전구 1개로 본다.</b> 우리가 수집하는 건 충전기 단위 상태 포인트
 * {@code st} 하나뿐이라 충전구를 구분할 근거가 없다. 실제 급속기에 커넥터가 여러 개라면
 * XN 에서 충전구별 포인트를 받아야 하고, 그때 배열에 항목이 늘어난다
 * (배열 구조를 유지해 둔 이유다).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChargerApiService {

    /** 충전기 상태 포인트 태그 */
    private static final String TAG_STATUS = "st";

    private static final String TYPE_FAST = "DC_FAST";
    private static final String TYPE_SLOW = "AC_SLOW";

    private final ChargerRepository chargerRepository;
    private final UnityLocZoneRepository zoneRepository;
    private final ZoneCascadeService zoneCascadeService;

    /**
     * 충전소별 충전기 목록.
     *
     * @param stationId 충전소 구역의 {@code zone_code}.
     *                  <b>비우면 전체 충전소</b>를 반환한다(현재 충전소가 한 곳뿐이라 실질 동일).
     * @return 충전소 단위로 묶은 목록. 해당 stationId 가 없으면 빈 목록
     */
    @Transactional(readOnly = true)
    public List<HqsStationItem> getChargerList(String stationId) {
        List<Device> chargers;

        if (StringUtils.hasText(stationId)) {
            UnityLocZone station = zoneRepository.findByZoneCode(stationId.trim()).orElse(null);
            if (station == null) {
                log.warn("존재하지 않는 stationId={}", stationId);
                return List.of();
            }
            // 충전소 하위에 자식 구역을 두는 경우까지 포함한다.
            List<Long> zoneIds = zoneCascadeService.resolveSubtreeZoneIds(station.getZoneId());
            if (zoneIds == null || zoneIds.isEmpty()) {
                return List.of();
            }
            chargers = chargerRepository.findChargersByZoneIds(zoneIds);
        } else {
            chargers = chargerRepository.findAllChargers();
        }

        return groupByStation(chargers);
    }

    /* ──────────────────────────────────────────────────────────── */

    /**
     * 배치된 구역 기준으로 충전소를 묶는다.
     *
     * <p>{@code LinkedHashMap} 을 쓰는 이유 — 쿼리가 zone_id·카테고리·device_id 순으로 정렬해
     * 내려주는데, 일반 HashMap 이면 그 순서가 흐트러져 응답이 호출마다 달라진다.</p>
     */
    private List<HqsStationItem> groupByStation(List<Device> chargers) {
        Map<Long, List<Device>> byZone = new LinkedHashMap<>();
        Map<Long, UnityLocZone> zoneById = new LinkedHashMap<>();

        for (Device device : chargers) {
            DevicePlacement placement = device.getPlacement();
            UnityLocZone zone = (placement != null) ? placement.getUnityZone() : null;
            if (zone == null) {
                // 배치되지 않은 충전기는 소속 충전소를 알 수 없어 응답에서 제외한다.
                log.warn("배치되지 않은 충전기 제외 deviceKey={}", device.getDeviceKey());
                continue;
            }
            zoneById.putIfAbsent(zone.getZoneId(), zone);
            byZone.computeIfAbsent(zone.getZoneId(), k -> new ArrayList<>()).add(device);
        }

        List<HqsStationItem> stations = new ArrayList<>(byZone.size());
        byZone.forEach((zoneId, devices) -> {
            UnityLocZone zone = zoneById.get(zoneId);
            stations.add(HqsStationItem.builder()
                    // zone_code 가 비어 있으면 stationId 를 줄 수 없다. 등록이 필요하다는 신호로 남긴다.
                    .stationId(zone.getZoneCode())
                    .stationName(zone.getZoneName())
                    .chargers(devices.stream().map(this::toChargerItem).toList())
                    .build());
            if (!StringUtils.hasText(zone.getZoneCode())) {
                log.warn("zone_code 미등록 구역이라 stationId 가 null 로 나갑니다. zoneId={} name={}",
                        zone.getZoneId(), zone.getZoneName());
            }
        });
        return stations;
    }

    private HqsChargerItem toChargerItem(Device device) {
        XnPointData statusPoint = findStatusPoint(device);

        HqsConnectorItem connector = HqsConnectorItem.builder()
                .status(ChargerStatus.fromRaw(statusPoint != null ? statusPoint.valueRaw() : null))
                .updatedAt(statusPoint != null ? statusPoint.updateDatetime() : lastUpdatedOf(device))
                .build();

        return HqsChargerItem.builder()
                .chargerId(device.getDeviceKey())
                .chargerName(device.getDeviceName())
                .type(resolveType(device))
                .connectors(List.of(connector))
                .build();
    }

    /** 카테고리 소분류 코드 → HQS 충전기 타입. */
    private String resolveType(Device device) {
        String code = (device.getCategory() != null) ? device.getCategory().getCategoryCode() : null;
        if (ChargerRepository.CATEGORY_FAST.equals(code)) {
            return TYPE_FAST;
        }
        if (ChargerRepository.CATEGORY_SLOW.equals(code)) {
            return TYPE_SLOW;
        }
        log.warn("충전기 타입을 판정할 수 없습니다. deviceKey={} categoryCode={}", device.getDeviceKey(), code);
        return null;
    }

    private XnPointData findStatusPoint(Device device) {
        DeviceCurrentValue currentValue = device.getDeviceCurrentValue();
        if (currentValue == null || currentValue.getPoints() == null) {
            return null;
        }
        return currentValue.getPoints().stream()
                .filter(point -> TAG_STATUS.equalsIgnoreCase(point.tagName()))
                .findFirst()
                .orElse(null);
    }

    /** 상태 포인트가 없을 때의 대체 시각 — 행 자체의 갱신 시각. */
    private OffsetDateTime lastUpdatedOf(Device device) {
        DeviceCurrentValue currentValue = device.getDeviceCurrentValue();
        return currentValue != null ? currentValue.getUpdatedAt() : null;
    }
}
