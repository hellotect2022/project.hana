package com.mpole.hana.digitaltwin.dashboard.energy.service;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.dashboard.energy.dto.EnergyGasKpiResponse;
import com.mpole.hana.digitaltwin.dashboard.energy.dto.EnergyHeatKpiResponse;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.energy.dto.EnergySummary;
import com.mpole.hana.digitaltwin.energy.service.EnergySummaryService;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import com.mpole.hana.digitaltwin.dashboard.config.DashboardCacheConfig;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceCurrentValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardEnergyService {

    private static final List<String> GAS_USAGE_TAGS = List.of("G", "total-g");

    private final DeviceRepository deviceRepository;
    private final DeviceCurrentValueRepository deviceCurrentValueRepository;
    private final EnergySummaryService energySummaryService;

    /** 가스미터(RM_GM) G(m³) 누적값 합산. */
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = DashboardCacheConfig.SITUATION_CACHE,
            cacheManager = "dashboardCacheManager",
            key = "'energyGas'")
    public EnergyGasKpiResponse getGasKpi() {
        List<Device> meters = deviceRepository.fetchGasMeterDevices();
        Set<Long> meterIds = meters.stream().map(Device::getDeviceId).collect(Collectors.toSet());
        Map<Long, DeviceCurrentValue> currentValueMap = meterIds.isEmpty()
                ? Map.of()
                : deviceCurrentValueRepository.findAllById(meterIds).stream()
                        .collect(Collectors.toMap(DeviceCurrentValue::getDeviceId, cv -> cv, (a, b) -> a));

        double cumulativeM3 = 0;
        int measuring = 0;
        for (Device meter : meters) {
            DeviceCurrentValue currentValue = currentValueMap.get(meter.getDeviceId());
            if (currentValue == null || currentValue.getPoints() == null || currentValue.getPoints().isEmpty()) {
                continue;
            }
            Double g = sumGasM3(currentValue.getPoints());
            if (g != null && g > 0) {
                measuring++;
                cumulativeM3 += g;
            }
        }

        List<Long> gasIds = meters.stream().map(Device::getDeviceId).toList();
        List<EnergySummary> gasSummaries = gasIds.isEmpty()
                ? List.of()
                : energySummaryService.summary(gasIds, GAS_USAGE_TAGS);
        double todayGasM3 = sumTodayUsage(gasSummaries);
        double weekGasM3 = sumPeriodUsage(gasSummaries, EnergySummary::week);
        double monthGasM3 = sumPeriodUsage(gasSummaries, EnergySummary::month);
        boolean todayLinked = hasTodayUsage(gasSummaries);
        boolean weekLinked = hasPeriodUsage(gasSummaries, EnergySummary::week);
        boolean monthLinked = hasPeriodUsage(gasSummaries, EnergySummary::month);

        return EnergyGasKpiResponse.builder()
                .cumulativeM3(cumulativeM3)
                .todayNm3(todayLinked ? roundUsage3(todayGasM3) : null)
                .weekNm3(weekLinked ? roundUsage3(weekGasM3) : null)
                .monthNm3(monthLinked ? roundUsage3(monthGasM3) : null)
                .meterTotal(meters.size())
                .measuringCount(measuring)
                .linked(measuring > 0 || todayLinked || weekLinked || monthLinked)
                .todayLinked(todayLinked)
                .weekLinked(weekLinked)
                .monthLinked(monthLinked)
                .source("tbl_device + tbl_device_current_value (RM_GM G)")
                .todaySource("EnergySummary RM_GM G/total-g + tbl_energy_snapshot (금일·금주·금월 Nm³)")
                .build();
    }

    /** 지역난방 — RM_* 검침·Gcal 포인트 미등록 시 linked=false. */
    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = DashboardCacheConfig.SITUATION_CACHE,
            cacheManager = "dashboardCacheManager",
            key = "'energyHeat'")
    public EnergyHeatKpiResponse getHeatKpi() {
        return EnergyHeatKpiResponse.builder()
                .linked(false)
                .todayGcal(null)
                .meterTotal(0)
                .source("지역난방 검침 장비(RM/누적 Gcal) 미등록")
                .build();
    }

    private static double sumTodayUsage(List<EnergySummary> summaries) {
        return sumPeriodUsage(summaries, EnergySummary::today);
    }

    private static double sumPeriodUsage(
            List<EnergySummary> summaries,
            java.util.function.Function<EnergySummary, EnergySummary.Period> periodGetter) {
        return summaries.stream()
                .map(periodGetter)
                .filter(Objects::nonNull)
                .map(EnergySummary.Period::value)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private static boolean hasTodayUsage(List<EnergySummary> summaries) {
        return hasPeriodUsage(summaries, EnergySummary::today);
    }

    private static boolean hasPeriodUsage(
            List<EnergySummary> summaries,
            java.util.function.Function<EnergySummary, EnergySummary.Period> periodGetter) {
        return summaries.stream()
                .anyMatch(s -> {
                    EnergySummary.Period p = periodGetter.apply(s);
                    return p != null && p.value() != null;
                });
    }

    private static double roundUsage3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
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
