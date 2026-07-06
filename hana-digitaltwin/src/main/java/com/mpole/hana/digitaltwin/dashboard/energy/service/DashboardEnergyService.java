package com.mpole.hana.digitaltwin.dashboard.energy.service;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.dashboard.energy.dto.EnergyGasKpiResponse;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceSnapshotLast;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceSnapshotLastRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardEnergyService {

    private final DeviceRepository deviceRepository;
    private final DeviceSnapshotLastRepository deviceSnapshotLastRepository;

    /** 가스미터(RM_GM) G(m³) 누적값 합산. */
    @Transactional(readOnly = true)
    public EnergyGasKpiResponse getGasKpi() {
        List<Device> meters = deviceRepository.fetchGasMeterDevices();
        Set<Long> meterIds = meters.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        Map<Long, DeviceSnapshotLast> currentValueMap = meterIds.isEmpty()
                ? Map.of()
                : deviceSnapshotLastRepository.findAllById(meterIds).stream()
                        .collect(Collectors.toMap(DeviceSnapshotLast::getDeviceId, cv -> cv, (a, b) -> a));

        double cumulativeM3 = 0;
        int measuring = 0;
        for (Device meter : meters) {
            DeviceSnapshotLast currentValue = currentValueMap.get(meter.getDeviceId());
            if (currentValue == null || currentValue.getPoints() == null || currentValue.getPoints().isEmpty()) {
                continue;
            }
            Double g = sumGasM3(currentValue.getPoints());
            if (g != null && g > 0) {
                measuring++;
                cumulativeM3 += g;
            }
        }

        return EnergyGasKpiResponse.builder()
                .cumulativeM3(cumulativeM3)
                .meterTotal(meters.size())
                .measuringCount(measuring)
                .linked(measuring > 0)
                .source("tbl_device + tbl_device_current_value (RM_GM G)")
                .build();
    }

    private static Double sumGasM3(List<XnPointData> points) {
        for (XnPointData point : points) {
            if (point != null && "G".equals(point.tagName())) {
                return parseDouble(point.valueRaw());
            }
        }
        return null;
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
