package com.mpole.hana.digitaltwin.dashboard.power.service;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerMccRuntimeItemResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerMccRuntimeResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerSubstationRuntimeItemResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerSubstationRuntimeResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerDailyKwhPointResponse;
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
import com.mpole.hana.digitaltwin.energy.dto.EnergySummary;
import com.mpole.hana.digitaltwin.dashboard.config.DashboardCacheConfig;
import com.mpole.hana.digitaltwin.energy.service.EnergySummaryService;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceCurrentValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardPowerService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final List<String> ELECTRIC_USAGE_TAGS = List.of(
            "kWh", "total_kWh", "KWH", "total-kWh");

    private final DeviceRepository deviceRepository;
    private final DeviceCurrentValueRepository deviceCurrentValueRepository;
    private final EnergySummaryService energySummaryService;
    private final PowerUsageChartService powerUsageChartService;

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
    @Cacheable(cacheNames = DashboardCacheConfig.POWER_CACHE, cacheManager = "dashboardCacheManager", key = "'kpi'")
    public PowerKpiResponse getKpi() {
        List<Device> meters = deviceRepository.fetchElectricMeterDevices();
        Set<Long> meterIds = meters.stream().map(Device::getDeviceId).collect(Collectors.toSet());

        Map<Long, DeviceCurrentValue> currentValueMap = deviceCurrentValueRepository.findAllById(meterIds).stream()
                .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));

        double cumulativeKwh = 0;
        int runningCount = 0;
        for (Device meter : meters) {
            DeviceCurrentValue currentValue = currentValueMap.get(meter.getDeviceId());
            if (currentValue == null || currentValue.getPoints() == null || currentValue.getPoints().isEmpty()) {
                continue;
            }
            runningCount++;
            cumulativeKwh += sumTotalKwh(currentValue.getPoints());
        }

        List<Long> meterIdsList = meters.stream().map(Device::getDeviceId).toList();
        List<EnergySummary> usageSummaries = meterIdsList.isEmpty()
                ? List.of()
                : energySummaryService.summary(meterIdsList, ELECTRIC_USAGE_TAGS);

        double todayKwh = sumUsagePeriod(usageSummaries, EnergySummary::today);
        double weekKwh = sumUsagePeriod(usageSummaries, EnergySummary::week);
        double monthKwh = sumUsagePeriod(usageSummaries, EnergySummary::month);

        boolean todayLinked = hasUsagePeriod(usageSummaries, EnergySummary::today);
        boolean weekLinked = hasUsagePeriod(usageSummaries, EnergySummary::week);
        boolean monthLinked = hasUsagePeriod(usageSummaries, EnergySummary::month);

        List<Long> usagePointIds = usageSummaries.stream()
                .map(EnergySummary::pointId)
                .distinct()
                .toList();
        PowerUsageChartService.ChartBundle charts = powerUsageChartService.buildCharts(usagePointIds);
        List<PowerDailyKwhPointResponse> weekDailyKwh = buildWeekDailyKwh(usagePointIds);
        List<PowerDailyKwhPointResponse> monthDailyKwh = buildMonthDailyKwh(usagePointIds);

        return PowerKpiResponse.builder()
                .cumulativeKwh(cumulativeKwh)
                .runningCount(runningCount)
                .meterTotal(meters.size())
                .todayKwh(todayLinked ? roundUsage3(todayKwh) : null)
                .weekKwh(weekLinked ? roundUsage3(weekKwh) : null)
                .monthKwh(monthLinked ? roundUsage3(monthKwh) : null)
                .todayLinked(todayLinked)
                .weekLinked(weekLinked)
                .monthLinked(monthLinked)
                .realtimeSeries(charts.realtimeSeries())
                .peakDemand(charts.peakDemand())
                .weekDailyKwh(weekDailyKwh)
                .monthDailyKwh(monthDailyKwh)
                .mainChartsLinked(charts.linked())
                .phaseBalance(buildPhaseBalance())
                .source("tbl_device + tbl_device_current_value (KWH/total_kWh)")
                .usageSource("EnergySummary RM_KWH 누적 포인트 + tbl_energy_snapshot (금일·금주·금월 kWh)")
                .stub(false)
                .build();
    }

    private List<PowerDailyKwhPointResponse> buildWeekDailyKwh(List<Long> usagePointIds) {
        if (usagePointIds == null || usagePointIds.isEmpty()) {
            return List.of();
        }
        LocalDate today = OffsetDateTime.now(KST).toLocalDate();
        return toDailyKwhResponses(
                powerUsageChartService.buildDailyKwhSeries(usagePointIds, today.minusDays(6), today));
    }

    private List<PowerDailyKwhPointResponse> buildMonthDailyKwh(List<Long> usagePointIds) {
        if (usagePointIds == null || usagePointIds.isEmpty()) {
            return List.of();
        }
        LocalDate today = OffsetDateTime.now(KST).toLocalDate();
        return toDailyKwhResponses(
                powerUsageChartService.buildDailyKwhSeries(usagePointIds, today.withDayOfMonth(1), today));
    }

    private static List<PowerDailyKwhPointResponse> toDailyKwhResponses(
            List<PowerUsageChartService.DailyKwhPoint> rows) {
        return rows.stream()
                .map(p -> PowerDailyKwhPointResponse.builder()
                        .date(p.date())
                        .kwh(p.kwh())
                        .build())
                .toList();
    }

    private static double sumUsagePeriod(
            List<EnergySummary> summaries,
            Function<EnergySummary, EnergySummary.Period> periodGetter) {
        return summaries.stream()
                .map(periodGetter)
                .filter(Objects::nonNull)
                .map(EnergySummary.Period::value)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private static boolean hasUsagePeriod(
            List<EnergySummary> summaries,
            Function<EnergySummary, EnergySummary.Period> periodGetter) {
        return summaries.stream()
                .anyMatch(s -> {
                    EnergySummary.Period p = periodGetter.apply(s);
                    return p != null && p.value() != null;
                });
    }

    private static double roundUsage3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /**
     * 전력 구간(특고압/저압/발전기) 운용·등록 현황.
     * 각 설비의 V/A/kW와 통신상태(states)로 정상/이상 카운트를 산출한다.
     * 알람은 별도 알람 소스가 없어 항상 0.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = DashboardCacheConfig.POWER_CACHE, cacheManager = "dashboardCacheManager", key = "'section:' + #tabId")
    public PowerSectionResponse getSection(String tabId) {
        String tab = tabId == null ? "ehv" : tabId.toLowerCase();
        List<Device> devices = switch (tab) {
            case "lv" -> deviceRepository.fetchLowVoltageDevices();
            case "gen" -> deviceRepository.fetchGeneratorDevices();
            default -> deviceRepository.fetchHighVoltageDevices();
        };

        Set<Long> ids = devices.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        Map<Long, DeviceCurrentValue> snapshots = deviceCurrentValueRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));

        List<PowerSectionDeviceResponse> rows = new ArrayList<>();
        int normal = 0;
        int fault = 0;
        for (Device device : devices) {
            DeviceCurrentValue snapshot = snapshots.get(device.getDeviceId());
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
        Map<Long, DeviceCurrentValue> snapshots = deviceCurrentValueRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));

        List<XnPointData> points = null;
        for (Device incomer : incomers) {
            DeviceCurrentValue snapshot = snapshots.get(incomer.getDeviceId());
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

    /**
     * MCC 및 동력 계통도 — hotspot id(언더스코어) ↔ 설비 실시간 포인트.
     * device_name 이 MCC- 로 시작하는 Pan 은 hotspotId = MCC_B7_F3 형식.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = DashboardCacheConfig.POWER_CACHE, cacheManager = "dashboardCacheManager", key = "'mccRuntime'")
    public PowerMccRuntimeResponse getMccRuntime() {
        List<Device> devices = deviceRepository.fetchMccDiagramDevices();
        Set<Long> ids = devices.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        Map<Long, DeviceCurrentValue> snapshots = ids.isEmpty()
                ? Map.of()
                : deviceCurrentValueRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));

        List<PowerMccRuntimeItemResponse> items = new ArrayList<>();
        int linked = 0;
        for (Device device : devices) {
            String deviceName = device.getDeviceName() != null ? device.getDeviceName().trim() : "";
            String hotspotId = toMccHotspotId(deviceName);
            if (hotspotId == null) {
                continue;
            }
            DeviceCurrentValue snapshot = snapshots.get(device.getDeviceId());
            List<XnPointData> points = snapshot != null ? snapshot.getPoints() : null;
            boolean hasPoints = points != null && !points.isEmpty();
            Map<String, String> valuesByTag = hasPoints ? tagValueMap(points) : Map.of();
            boolean commOk = hasPoints && valuesByTag.values().stream().anyMatch(v -> v != null && !v.isBlank());
            if (commOk) {
                linked++;
            }
            items.add(PowerMccRuntimeItemResponse.builder()
                    .hotspotId(hotspotId)
                    .deviceId(device.getDeviceId())
                    .deviceKey(device.getDeviceKey())
                    .deviceName(deviceName)
                    .linked(hasPoints)
                    .commOk(commOk)
                    .updatedAt(snapshot != null && snapshot.getUpdatedAt() != null
                            ? snapshot.getUpdatedAt().toString() : null)
                    .valuesByTag(valuesByTag)
                    .build());
        }

        return PowerMccRuntimeResponse.builder()
                .items(items)
                .total(items.size())
                .linked(linked)
                .source("tbl_device + tbl_device_current_value (PS_MCC_%)")
                .build();
    }

    /**
     * 수변전 설비 — layout node id / hotspot id ↔ PS_PRD_* 설비 실시간 포인트.
     * device_name(예: SHV-b) 또는 device_key 마지막 세그먼트를 panelId 로 쓴다.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = DashboardCacheConfig.POWER_CACHE, cacheManager = "dashboardCacheManager", key = "'substationRuntime'")
    public PowerSubstationRuntimeResponse getSubstationRuntime() {
        List<Device> devices = deviceRepository.fetchSubstationDiagramDevices();
        Set<Long> ids = devices.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        Map<Long, DeviceCurrentValue> snapshots = ids.isEmpty()
                ? Map.of()
                : deviceCurrentValueRepository.findAllById(ids).stream()
                        .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));

        Map<String, PowerSubstationRuntimeItemResponse> byPanelId = new LinkedHashMap<>();

        for (Device device : devices) {
            String panelId = toSubstationPanelId(device);
            if (panelId == null) {
                continue;
            }
            DeviceCurrentValue snapshot = snapshots.get(device.getDeviceId());
            List<XnPointData> points = snapshot != null ? snapshot.getPoints() : null;
            boolean hasPoints = points != null && !points.isEmpty();
            Map<String, String> valuesByTag = hasPoints ? tagValueMap(points) : Map.of();
            boolean commOk = hasPoints && valuesByTag.values().stream().anyMatch(v -> v != null && !v.isBlank());

            String deviceName = device.getDeviceName() != null ? device.getDeviceName().trim() : "";
            PowerSubstationRuntimeItemResponse item = PowerSubstationRuntimeItemResponse.builder()
                    .panelId(panelId)
                    .deviceId(device.getDeviceId())
                    .deviceKey(device.getDeviceKey())
                    .deviceName(deviceName)
                    .linked(hasPoints)
                    .commOk(commOk)
                    .updatedAt(snapshot != null && snapshot.getUpdatedAt() != null
                            ? snapshot.getUpdatedAt().toString() : null)
                    .valuesByTag(valuesByTag)
                    .build();

            PowerSubstationRuntimeItemResponse existing = byPanelId.get(panelId);
            if (existing == null || (!existing.commOk() && item.commOk())) {
                byPanelId.put(panelId, item);
            }
        }

        List<PowerSubstationRuntimeItemResponse> items = new ArrayList<>(byPanelId.values());
        int linked = (int) items.stream().filter(PowerSubstationRuntimeItemResponse::commOk).count();
        return PowerSubstationRuntimeResponse.builder()
                .items(items)
                .total(items.size())
                .linked(linked)
                .source("tbl_device + tbl_device_current_value (PS_PRD_SHV/HV/LV, PS_PGE_GEN)")
                .build();
    }

    private static final Map<String, String> SUBSTATION_GENERATOR_PANEL_IDS = Map.of(
            "발전기1", "G1",
            "발전기2", "G2"
    );

    private static String toSubstationPanelId(Device device) {
        if (device == null) {
            return null;
        }
        String deviceName = device.getDeviceName() != null ? device.getDeviceName().trim() : "";
        if (!deviceName.isBlank()) {
            String gen = SUBSTATION_GENERATOR_PANEL_IDS.get(deviceName);
            if (gen != null) {
                return gen;
            }
            if (isSubstationDiagramPanelId(deviceName)) {
                return deviceName;
            }
        }
        String deviceKey = device.getDeviceKey() != null ? device.getDeviceKey().trim() : "";
        String code = extractCode(deviceKey);
        if (code.isBlank() || code.toUpperCase(Locale.ROOT).startsWith("BLANK")) {
            return fallbackSubstationLayoutNodeId(deviceKey);
        }
        String gen = SUBSTATION_GENERATOR_PANEL_IDS.get(code);
        if (gen != null) {
            return gen;
        }
        if (isSubstationDiagramPanelId(code)) {
            return code;
        }
        return fallbackSubstationLayoutNodeId(deviceKey);
    }

    /** 계통도 layout node id = device_key (예: PS_PRD_HV-I_SHV-b_ES) */
    private static String fallbackSubstationLayoutNodeId(String deviceKey) {
        if (deviceKey == null || deviceKey.isBlank()) {
            return null;
        }
        String key = deviceKey.trim();
        if (key.startsWith("PS_PRD_") || key.startsWith("PS_PGE_")) {
            return key;
        }
        return null;
    }

    private static boolean isSubstationDiagramPanelId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (id.startsWith("SHV-")) {
            return true;
        }
        if ("LV-R".equals(id)) {
            return true;
        }
        if (id.startsWith("LV-")) {
            return true;
        }
        return id.equals("G1") || id.equals("G2") || id.equals("G3-1") || id.equals("G3-2");
    }

    private static String toMccHotspotId(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return null;
        }
        if (!deviceName.toUpperCase(Locale.ROOT).startsWith("MCC-")) {
            return null;
        }
        return deviceName.replace('-', '_');
    }

    private static Map<String, String> tagValueMap(List<XnPointData> points) {
        Map<String, String> map = new LinkedHashMap<>();
        for (XnPointData point : points) {
            if (point == null || point.tagName() == null) {
                continue;
            }
            String tag = point.tagName().trim();
            String raw = point.valueRaw();
            map.putIfAbsent(tag, raw != null ? raw.trim() : null);
        }
        return map;
    }

    private static PowerTabResponse tab(String id, String label) {
        return PowerTabResponse.builder().id(id).label(label).build();
    }
}
