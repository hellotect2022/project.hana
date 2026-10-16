package com.mpole.hana.digitaltwin.dashboard.statistics.service;

import com.mpole.hana.digitaltwin.dashboard.config.DashboardCacheConfig;
import com.mpole.hana.digitaltwin.dashboard.power.service.PowerUsageChartService;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsEnergyTrendPointResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsEnergyTrendResponse;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.energy.dto.EnergySummary;
import com.mpole.hana.digitaltwin.energy.service.EnergySummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 통계·리포트 — 전기 사용량 추이 (전력·에너지 화면과 동일: EnergySummary + 15분 스냅샷).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsEnergyTrendService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MM'월'");
    private static final List<String> ELECTRIC_USAGE_TAGS = List.of(
            "kWh", "total_kWh", "KWH", "total-kWh");

    private final DeviceRepository deviceRepository;
    private final EnergySummaryService energySummaryService;
    private final PowerUsageChartService powerUsageChartService;

    @Cacheable(
            cacheNames = DashboardCacheConfig.SITUATION_CACHE,
            cacheManager = "dashboardCacheManager",
            key = "'statsEnergyTrend:' + #period")
    public StatisticsEnergyTrendResponse getEnergyTrend(String period) {
        String normalized = normalizePeriod(period);
        List<Long> pointIds = resolveElectricPointIds();
        List<EnergySummary> usageSummaries = resolveUsageSummaries();

        if (pointIds.isEmpty()) {
            return StatisticsEnergyTrendResponse.builder()
                    .period(normalized)
                    .points(List.of())
                    .linked(false)
                    .source("RM_KWH 검침 포인트 없음")
                    .build();
        }

        OffsetDateTime now = OffsetDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        List<StatisticsEnergyTrendPointResponse> points = buildPoints(normalized, pointIds, today, now);

        boolean summaryLinked = periodSummaryLinked(normalized, usageSummaries);
        boolean chartLinked = points.stream().anyMatch(p -> p.kwh() > 0);
        boolean linked = summaryLinked || chartLinked;

        return StatisticsEnergyTrendResponse.builder()
                .period(normalized)
                .points(points)
                .linked(linked)
                .source("EnergySummary + tbl_energy_snapshot (전력 KPI와 동일)")
                .build();
    }

    private static String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "month";
        }
        return switch (period.trim().toLowerCase()) {
            case "day", "week", "month", "quarter", "year" -> period.trim().toLowerCase();
            default -> "month";
        };
    }

    private List<Long> resolveElectricPointIds() {
        List<Device> meters = deviceRepository.fetchElectricMeterDevices();
        if (meters.isEmpty()) {
            return List.of();
        }
        List<Long> deviceIds = meters.stream().map(Device::getDeviceId).toList();
        return energySummaryService.summary(deviceIds, ELECTRIC_USAGE_TAGS).stream()
                .map(EnergySummary::pointId)
                .distinct()
                .toList();
    }

    private List<EnergySummary> resolveUsageSummaries() {
        List<Device> meters = deviceRepository.fetchElectricMeterDevices();
        if (meters.isEmpty()) {
            return List.of();
        }
        List<Long> deviceIds = meters.stream().map(Device::getDeviceId).toList();
        return energySummaryService.summary(deviceIds, ELECTRIC_USAGE_TAGS);
    }

    private static boolean periodSummaryLinked(String period, List<EnergySummary> summaries) {
        return switch (period) {
            case "day" -> hasUsagePeriod(summaries, EnergySummary::today);
            case "week" -> hasUsagePeriod(summaries, EnergySummary::week);
            case "month", "quarter", "year" -> hasUsagePeriod(summaries, EnergySummary::month)
                    || hasUsagePeriod(summaries, EnergySummary::week);
            default -> hasUsagePeriod(summaries, EnergySummary::month);
        };
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

    private List<StatisticsEnergyTrendPointResponse> buildPoints(
            String period,
            List<Long> pointIds,
            LocalDate today,
            OffsetDateTime now) {
        return switch (period) {
            case "day" -> mapDailyKwh(powerUsageChartService.buildTodayHourlyKwh(pointIds));
            case "week" -> mapDailyKwh(
                    powerUsageChartService.buildDailyKwhSeries(pointIds, today.minusDays(6), today));
            case "month" -> mapDailyKwh(
                    powerUsageChartService.buildDailyKwhSeries(
                            pointIds, today.withDayOfMonth(1), today));
            case "quarter" -> buildYearQuarters(pointIds, today);
            case "year" -> buildYearMonths(pointIds, today);
            default -> mapDailyKwh(
                    powerUsageChartService.buildDailyKwhSeries(
                            pointIds, today.withDayOfMonth(1), today));
        };
    }

    private static List<StatisticsEnergyTrendPointResponse> mapDailyKwh(
            List<PowerUsageChartService.DailyKwhPoint> rows) {
        return rows.stream()
                .map(r -> StatisticsEnergyTrendPointResponse.builder()
                        .date(r.date())
                        .kwh(r.kwh())
                        .build())
                .toList();
    }

    /** 올해 1~4분기별 kWh 합계 */
    private List<StatisticsEnergyTrendPointResponse> buildYearQuarters(
            List<Long> pointIds,
            LocalDate today) {
        int year = today.getYear();
        List<StatisticsEnergyTrendPointResponse> out = new ArrayList<>();
        for (int q = 1; q <= 4; q++) {
            int monthStart = (q - 1) * 3 + 1;
            LocalDate qStart = LocalDate.of(year, monthStart, 1);
            if (qStart.isAfter(today)) {
                out.add(StatisticsEnergyTrendPointResponse.builder()
                        .date(q + "분기")
                        .kwh(0)
                        .build());
                continue;
            }
            LocalDate qEnd = qStart.plusMonths(3).minusDays(1);
            if (qEnd.isAfter(today)) {
                qEnd = today;
            }
            double kwh = sumDailyKwh(pointIds, qStart, qEnd);
            out.add(StatisticsEnergyTrendPointResponse.builder()
                    .date(q + "분기")
                    .kwh(round3(kwh))
                    .build());
        }
        return out;
    }

    private List<StatisticsEnergyTrendPointResponse> buildYearMonths(
            List<Long> pointIds,
            LocalDate today) {
        List<StatisticsEnergyTrendPointResponse> out = new ArrayList<>();
        int year = today.getYear();
        for (int m = 1; m <= 12; m++) {
            LocalDate mStart = LocalDate.of(year, m, 1);
            if (mStart.isAfter(today)) {
                out.add(StatisticsEnergyTrendPointResponse.builder()
                        .date(mStart.format(MONTH_LABEL))
                        .kwh(0)
                        .build());
                continue;
            }
            LocalDate mEnd = mStart.plusMonths(1).minusDays(1);
            if (mEnd.isAfter(today)) {
                mEnd = today;
            }
            double kwh = sumDailyKwh(pointIds, mStart, mEnd);
            out.add(StatisticsEnergyTrendPointResponse.builder()
                    .date(mStart.format(MONTH_LABEL))
                    .kwh(round3(kwh))
                    .build());
        }
        return out;
    }

    private double sumDailyKwh(List<Long> pointIds, LocalDate from, LocalDate to) {
        return powerUsageChartService.buildDailyKwhSeries(pointIds, from, to).stream()
                .mapToDouble(PowerUsageChartService.DailyKwhPoint::kwh)
                .sum();
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
