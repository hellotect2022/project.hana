package com.mpole.hana.digitaltwin.dashboard.power.service;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerMetricResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerPageSnapshotResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerPhaseBalanceResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerPhaseValueResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerSectionDeviceResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerSectionResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerTabResponse;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceSnapshotLast;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceSnapshotLastRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardPowerService {

    private final DeviceRepository deviceRepository;
    private final DeviceSnapshotLastRepository deviceSnapshotLastRepository;

    @Transactional(readOnly = true)
    public PowerPageSnapshotResponse getPageSnapshot() {
        return PowerPageSnapshotResponse.builder()
                .tabs(List.of(
                        tab("ehv", "특고압"),
                        tab("lv", "저압"),
                        tab("gen", "발전기")
                ))
                .activeTabId("ehv")
                .stub(true)
                .build();
    }

    @Transactional(readOnly = true)
    public PowerKpiResponse getKpi() {
        List<Device> meters = deviceRepository.fetchElectricMeterDevices();
        Set<Long> meterIds = meters.stream().map(Device::getDeviceId).collect(Collectors.toSet());

        Map<Long, DeviceSnapshotLast> currentValueMap = deviceSnapshotLastRepository.findAllById(meterIds).stream()
                .collect(Collectors.toMap(DeviceSnapshotLast::getDeviceId, cv -> cv, (a, b) -> a));

        double cumulativeKwh = 0;
        int runningCount = 0;
        for (Device meter : meters) {
            DeviceSnapshotLast currentValue = currentValueMap.get(meter.getDeviceId());
            if (currentValue == null || currentValue.getPoints() == null || currentValue.getPoints().isEmpty()) {
                continue;
            }
            runningCount++;
            cumulativeKwh += sumTotalKwh(currentValue.getPoints());
        }

        return PowerKpiResponse.builder()
                .cumulativeKwh(cumulativeKwh)
                .runningCount(runningCount)
                .meterTotal(meters.size())
                .phaseBalance(buildPhaseBalance())
                .source("tbl_device + tbl_device_current_value (KWH/total_kWh)")
                .stub(false)
                .build();
    }

    /**
     * 전력 구간(특고압/저압/발전기) 운용·등록 현황.
     * 각 설비의 V/A/kW와 통신상태(states)로 정상/이상 카운트를 산출한다.
     * 알람은 별도 알람 소스가 없어 항상 0.
     */
    @Transactional(readOnly = true)
    public PowerSectionResponse getSection(String tabId) {
        String tab = tabId == null ? "ehv" : tabId.toLowerCase();
        List<Device> devices = switch (tab) {
            case "lv" -> deviceRepository.fetchLowVoltageDevices();
            case "gen" -> deviceRepository.fetchGeneratorDevices();
            default -> deviceRepository.fetchHighVoltageDevices();
        };

        Set<Long> ids = devices.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        Map<Long, DeviceSnapshotLast> snapshots = deviceSnapshotLastRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DeviceSnapshotLast::getDeviceId, cv -> cv, (a, b) -> a));

        List<PowerSectionDeviceResponse> rows = new ArrayList<>();
        int normal = 0;
        int fault = 0;
        for (Device device : devices) {
            DeviceSnapshotLast snapshot = snapshots.get(device.getDeviceId());
            List<XnPointData> points = snapshot == null ? null : snapshot.getPoints();
            boolean hasData = points != null && !points.isEmpty();

            Double states = hasData ? pointValue(points, "states") : null;
            // states=0 또는 데이터 없음 → 통신 이상, 그 외 정상
            boolean comm = hasData && (states == null || states != 0d);
            if (comm) {
                normal++;
            } else {
                fault++;
            }

            rows.add(PowerSectionDeviceResponse.builder()
                    .id(extractCode(device.getDeviceKey()))
                    .v(hasData ? pointValue(points, "V") : null)
                    .a(hasData ? pointValue(points, "A") : null)
                    .kw(hasData ? pointValue(points, "kW") : null)
                    .comm(comm ? "normal" : "fault")
                    .build());
        }

        return PowerSectionResponse.builder()
                .tabId(tab)
                .total(devices.size())
                .normal(normal)
                .fault(fault)
                .alarm(0)
                .devices(rows)
                .linked(!devices.isEmpty())
                .source("tbl_device + tbl_device_current_value (PS_PRD_HV/LV, PS_PGE_GEN)")
                .build();
    }

    private static String extractCode(String deviceKey) {
        if (deviceKey == null || deviceKey.isBlank()) {
            return "";
        }
        int idx = deviceKey.lastIndexOf('_');
        return idx >= 0 ? deviceKey.substring(idx + 1) : deviceKey;
    }

    /**
     * 한전 인입(PS_PRD_SHV)의 선간전압/상전류/주파수/역률 순시값으로 상별 균형을 구성한다.
     * 데이터가 없으면 null을 반환(프론트에서 "데이터 없음" 처리).
     */
    private PowerPhaseBalanceResponse buildPhaseBalance() {
        List<Device> incomers = deviceRepository.fetchMainIncomerDevices();
        if (incomers.isEmpty()) {
            return null;
        }
        Set<Long> ids = incomers.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        Map<Long, DeviceSnapshotLast> snapshots = deviceSnapshotLastRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DeviceSnapshotLast::getDeviceId, cv -> cv, (a, b) -> a));

        List<XnPointData> points = null;
        for (Device incomer : incomers) {
            DeviceSnapshotLast snapshot = snapshots.get(incomer.getDeviceId());
            if (snapshot != null && snapshot.getPoints() != null && !snapshot.getPoints().isEmpty()) {
                points = snapshot.getPoints();
                break;
            }
        }
        if (points == null) {
            return null;
        }

        Double rs = pointValue(points, "R-S");
        Double st = pointValue(points, "S-T");
        Double tr = pointValue(points, "T-R");
        Double ra = pointValue(points, "RA");
        Double sa = pointValue(points, "SA");
        Double ta = pointValue(points, "TA");
        Double hz = pointValue(points, "HZ");
        Double pf = pointValue(points, "PF");

        if (rs == null && ra == null) {
            return null;
        }

        List<PowerPhaseValueResponse> voltage = new ArrayList<>();
        if (rs != null) voltage.add(PowerPhaseValueResponse.builder().phase("R-S").v(rs).build());
        if (st != null) voltage.add(PowerPhaseValueResponse.builder().phase("S-T").v(st).build());
        if (tr != null) voltage.add(PowerPhaseValueResponse.builder().phase("T-R").v(tr).build());

        List<PowerPhaseValueResponse> current = new ArrayList<>();
        if (ra != null) current.add(PowerPhaseValueResponse.builder().phase("R").a(ra).build());
        if (sa != null) current.add(PowerPhaseValueResponse.builder().phase("S").a(sa).build());
        if (ta != null) current.add(PowerPhaseValueResponse.builder().phase("T").a(ta).build());

        return PowerPhaseBalanceResponse.builder()
                .frequency(frequencyMetric(hz))
                .powerFactor(powerFactorMetric(pf))
                .voltage(voltage)
                .current(current)
                .build();
    }

    /** 주파수: 60Hz ± 0.5 정상, 벗어나면 이상. */
    private static PowerMetricResponse frequencyMetric(Double hz) {
        if (hz == null) {
            return PowerMetricResponse.builder().value(0).status("normal").label("--").build();
        }
        boolean normal = hz >= 59.5 && hz <= 60.5;
        return PowerMetricResponse.builder()
                .value(hz)
                .status(normal ? "normal" : "abnormal")
                .label(normal ? "정상" : "경보")
                .build();
    }

    /** 역률: 원시값이 0~1 비율이면 %로 환산. 85% 이상 정상. */
    private static PowerMetricResponse powerFactorMetric(Double pf) {
        if (pf == null) {
            return PowerMetricResponse.builder().value(0).status("normal").label("--").build();
        }
        double pct = pf <= 1.0 ? pf * 100.0 : pf;
        boolean normal = pct >= 85.0;
        return PowerMetricResponse.builder()
                .value(pct)
                .status(normal ? "normal" : "abnormal")
                .label(normal ? "정상" : "주의")
                .build();
    }

    private static Double pointValue(List<XnPointData> points, String tagName) {
        for (XnPointData point : points) {
            if (point != null && tagName.equals(point.tagName())) {
                String raw = point.valueRaw();
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
        return null;
    }

    private static double sumTotalKwh(List<XnPointData> points) {
        double sum = 0;
        for (XnPointData point : points) {
            if (point == null || !"total_kWh".equals(point.tagName())) {
                continue;
            }
            sum += parseDouble(point.valueRaw());
        }
        return sum;
    }

    private static double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static PowerTabResponse tab(String id, String label) {
        return PowerTabResponse.builder().id(id).label(label).build();
    }
}
