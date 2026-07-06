package com.mpole.hana.digitaltwin.dashboard.metering.service;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.dashboard.metering.dto.MeteringSituationPanelResponse;
import com.mpole.hana.digitaltwin.dashboard.metering.dto.MeteringUtilityItemResponse;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceCurrentValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardMeteringService {

    private static final String PENDING_NOTE = "데이터 확인 안됨";

    private static final List<UtilityMeta> UTILITY_META = List.of(
            new UtilityMeta("elec", "전기", true),
            new UtilityMeta("water", "수도", false),
            new UtilityMeta("hot", "온수", false),
            new UtilityMeta("gas", "가스", true),
            new UtilityMeta("hvac", "냉난방", false),
            new UtilityMeta("drain", "영구배수", false),
            new UtilityMeta("reuse", "중수/우수", false),
            new UtilityMeta("flow", "유량", false)
    );

    private final DeviceRepository deviceRepository;
    private final DeviceCurrentValueRepository deviceCurrentValueRepository;

    @Transactional(readOnly = true)
    public MeteringSituationPanelResponse getSituationUtilities() {
        Counts elec = countMeters(deviceRepository.fetchElectricMeterDevices());
        Counts gas = countMeters(deviceRepository.fetchGasMeterDevices());

        List<MeteringUtilityItemResponse> utilities = UTILITY_META.stream()
                .map(meta -> {
                    if (!meta.linked()) {
                        return MeteringUtilityItemResponse.builder()
                                .id(meta.id())
                                .label(meta.label())
                                .measuring(0)
                                .disconnected(0)
                                .total(0)
                                .linked(false)
                                .note(PENDING_NOTE)
                                .build();
                    }
                    Counts counts = "gas".equals(meta.id()) ? gas : elec;
                    return MeteringUtilityItemResponse.builder()
                            .id(meta.id())
                            .label(meta.label())
                            .measuring(counts.measuring())
                            .disconnected(counts.disconnected())
                            .total(counts.total())
                            .linked(true)
                            .note(null)
                            .build();
                })
                .toList();

        return MeteringSituationPanelResponse.builder()
                .utilities(utilities)
                .source("tbl_device + tbl_device_current_value (KWH, RM_GM)")
                .stub(false)
                .build();
    }

    private Counts countMeters(List<Device> meters) {
        Set<Long> meterIds = meters.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        Map<Long, DeviceCurrentValue> currentValueMap = meterIds.isEmpty()
                ? Map.of()
                : deviceCurrentValueRepository.findAllById(meterIds).stream()
                        .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));

        int measuring = 0;
        int disconnected = 0;
        for (Device device : meters) {
            if (hasCurrentValue(currentValueMap.get(device.getDeviceId()))) {
                measuring++;
            } else {
                disconnected++;
            }
        }
        return new Counts(measuring, disconnected, meters.size());
    }

    private static boolean hasCurrentValue(DeviceCurrentValue currentValue) {
        if (currentValue == null) {
            return false;
        }
        List<XnPointData> points = currentValue.getPoints();
        return points != null && !points.isEmpty();
    }

    private record UtilityMeta(String id, String label, boolean linked) {
    }

    private record Counts(int measuring, int disconnected, int total) {
    }
}
