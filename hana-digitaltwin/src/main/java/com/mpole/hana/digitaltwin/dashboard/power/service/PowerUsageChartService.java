package com.mpole.hana.digitaltwin.dashboard.power.service;

import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerPeakDemandPointResponse;
import com.mpole.hana.digitaltwin.dashboard.power.dto.PowerRealtimePointResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 전력감시 주요값 — energy 스냅샷(5분) 기반 15분 사용량·일별 Peak(kW) 추정.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PowerUsageChartService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MM/dd");
    /** 오늘 포함 D-10 ~ D-0 (11일) */
    private static final int PEAK_DAYS_BACK = 10;
    private static final int INTERVAL_MINUTES = 15;
    /** 누적 검침 갭·초기 적재 시 비정상 구간 delta 제외 (15분 합산 kWh) */
    private static final double MAX_INTERVAL_KWH = 20_000.0;

    private final NamedParameterJdbcTemplate jdbc;

    public record ChartBundle(
            List<PowerRealtimePointResponse> realtimeSeries,
            List<PowerPeakDemandPointResponse> peakDemand,
            boolean linked
    ) {
        static ChartBundle empty() {
            return new ChartBundle(List.of(), List.of(), false);
        }
    }

    /** 통계·리포트 일별 kWh (15분 구간 합산, 전력 KPI 차트와 동일 스냅샷 로직). */
    public List<DailyKwhPoint> buildDailyKwhSeries(List<Long> pointIds, LocalDate fromDay, LocalDate toDay) {
        if (pointIds == null || pointIds.isEmpty() || fromDay == null || toDay == null) {
            return List.of();
        }
        if (fromDay.isAfter(toDay)) {
            return List.of();
        }

        OffsetDateTime now = OffsetDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        OffsetDateTime historyStart = fromDay.atStartOfDay(KST).toOffsetDateTime();
        List<SnapshotRow> snapshots = loadSnapshots(pointIds, historyStart, now);
        if (snapshots.isEmpty()) {
            return List.of();
        }

        List<DailyKwhPoint> out = new ArrayList<>();
        for (LocalDate d = fromDay; !d.isAfter(toDay); d = d.plusDays(1)) {
            if (d.isAfter(today)) {
                break;
            }
            double kwh = dailyUsageKwhForDay(snapshots, pointIds, d, today, now);
            out.add(new DailyKwhPoint(d.format(DATE_LABEL), round3(kwh)));
        }
        return out;
    }

    /** 당일 시간대별 kWh (15분 슬롯을 시 단위로 합산). */
    public List<DailyKwhPoint> buildTodayHourlyKwh(List<Long> pointIds) {
        ChartBundle bundle = buildCharts(pointIds);
        if (bundle.realtimeSeries().isEmpty()) {
            return List.of();
        }
        Map<String, Double> byHour = new HashMap<>();
        for (var row : bundle.realtimeSeries()) {
            String t = row.t();
            if (t == null || t.isBlank()) {
                continue;
            }
            String hour = t.length() >= 2 ? t.substring(0, 2) : t;
            byHour.merge(hour, row.kwh(), Double::sum);
        }
        List<DailyKwhPoint> out = new ArrayList<>();
        byHour.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.add(new DailyKwhPoint(e.getKey() + ":00", round3(e.getValue()))));
        return out;
    }

    public record DailyKwhPoint(String date, double kwh) {
    }

    public ChartBundle buildCharts(List<Long> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) {
            return ChartBundle.empty();
        }

        OffsetDateTime now = OffsetDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        OffsetDateTime todayStart = today.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime historyStart = today.minusDays(PEAK_DAYS_BACK).atStartOfDay(KST).toOffsetDateTime();

        List<SnapshotRow> snapshots = loadSnapshots(pointIds, historyStart, now);
        if (snapshots.isEmpty()) {
            return new ChartBundle(List.of(), zeroFilledPeakDays(today), false);
        }

        List<PowerRealtimePointResponse> realtime = buildTodayRealtime(
                snapshots, pointIds, todayStart, now);
        List<PowerPeakDemandPointResponse> peak = buildPeakDemand(
                snapshots, pointIds, today, now);

        boolean linked = !realtime.isEmpty() || peak.stream().anyMatch(p -> p.kw() > 0);
        return new ChartBundle(realtime, peak, linked);
    }

    private List<SnapshotRow> loadSnapshots(List<Long> pointIds, OffsetDateTime from, OffsetDateTime to) {
        String sql = """
                SELECT bucket_at, point_id, value
                  FROM tbl_energy_snapshot
                 WHERE point_id IN (:ids)
                   AND bucket_at >= :from
                   AND bucket_at <= :to
                 ORDER BY bucket_at ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", pointIds)
                .addValue("from", from)
                .addValue("to", to);
        return jdbc.query(sql, params, (rs, rowNum) -> new SnapshotRow(
                rs.getObject("bucket_at", OffsetDateTime.class).atZoneSameInstant(KST).toOffsetDateTime(),
                rs.getLong("point_id"),
                rs.getDouble("value")
        ));
    }

    private List<PowerRealtimePointResponse> buildTodayRealtime(
            List<SnapshotRow> snapshots,
            List<Long> pointIds,
            OffsetDateTime dayStart,
            OffsetDateTime now) {
        List<OffsetDateTime> slotEnds = fifteenMinuteSlots(dayStart, now);
        if (slotEnds.isEmpty()) {
            return List.of();
        }

        List<Double> totals = cumulativeTotalsAt(snapshots, pointIds, slotEnds);
        double baseline = baselineTotal(snapshots, pointIds, dayStart);
        double prev = baseline;

        List<PowerRealtimePointResponse> out = new ArrayList<>();
        for (int i = 0; i < slotEnds.size(); i++) {
            double total = totals.get(i);
            double usage = intervalUsageKwh(total, prev);
            prev = total;
            String label = slotEnds.get(i).toLocalTime().format(TIME_LABEL);
            out.add(PowerRealtimePointResponse.builder().t(label).kwh(round3(usage)).build());
        }
        return out;
    }

    private List<PowerPeakDemandPointResponse> buildPeakDemand(
            List<SnapshotRow> snapshots,
            List<Long> pointIds,
            LocalDate today,
            OffsetDateTime now) {
        List<PowerPeakDemandPointResponse> out = new ArrayList<>();
        for (int i = 0; i <= PEAK_DAYS_BACK; i++) {
            LocalDate day = today.minusDays(PEAK_DAYS_BACK - i);
            double peakKw = peakKwForDay(snapshots, pointIds, day, today, now);
            out.add(PowerPeakDemandPointResponse.builder()
                    .date(day.format(DATE_LABEL))
                    .kw(round3(peakKw))
                    .build());
        }
        return out;
    }

    private double dailyUsageKwhForDay(
            List<SnapshotRow> snapshots,
            List<Long> pointIds,
            LocalDate day,
            LocalDate today,
            OffsetDateTime now) {
        OffsetDateTime dayStart = day.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime dayEnd = day.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime slotEndLimit = day.equals(today) ? now : dayEnd.minusMinutes(INTERVAL_MINUTES);

        if (slotEndLimit.isBefore(dayStart.plusMinutes(INTERVAL_MINUTES))) {
            return 0;
        }

        List<OffsetDateTime> slotEnds = fifteenMinuteSlots(dayStart, slotEndLimit);
        if (slotEnds.isEmpty()) {
            return 0;
        }

        List<Double> totals = cumulativeTotalsAt(snapshots, pointIds, slotEnds);
        double baseline = baselineTotal(snapshots, pointIds, dayStart);
        double prev = baseline;
        double sumKwh = 0;

        for (double total : totals) {
            double usageKwh = intervalUsageKwh(total, prev);
            prev = total;
            sumKwh += usageKwh;
        }
        return sumKwh;
    }

    private double peakKwForDay(
            List<SnapshotRow> snapshots,
            List<Long> pointIds,
            LocalDate day,
            LocalDate today,
            OffsetDateTime now) {
        OffsetDateTime dayStart = day.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime dayEnd = day.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime slotEndLimit = day.equals(today) ? now : dayEnd.minusMinutes(INTERVAL_MINUTES);

        if (slotEndLimit.isBefore(dayStart.plusMinutes(INTERVAL_MINUTES))) {
            return 0;
        }

        List<OffsetDateTime> slotEnds = fifteenMinuteSlots(dayStart, slotEndLimit);
        if (slotEnds.isEmpty()) {
            return 0;
        }

        List<Double> totals = cumulativeTotalsAt(snapshots, pointIds, slotEnds);
        double baseline = baselineTotal(snapshots, pointIds, dayStart);
        double prev = baseline;
        double peakKw = 0;

        for (double total : totals) {
            double usageKwh = intervalUsageKwh(total, prev);
            prev = total;
            double kw = usageKwh * (60.0 / INTERVAL_MINUTES);
            if (kw > peakKw) {
                peakKw = kw;
            }
        }
        return peakKw;
    }

    private List<PowerPeakDemandPointResponse> zeroFilledPeakDays(LocalDate today) {
        List<PowerPeakDemandPointResponse> out = new ArrayList<>();
        for (int i = 0; i <= PEAK_DAYS_BACK; i++) {
            LocalDate day = today.minusDays(PEAK_DAYS_BACK - i);
            out.add(PowerPeakDemandPointResponse.builder()
                    .date(day.format(DATE_LABEL))
                    .kw(0.0)
                    .build());
        }
        return out;
    }

    private static List<OffsetDateTime> fifteenMinuteSlots(OffsetDateTime from, OffsetDateTime to) {
        List<OffsetDateTime> slots = new ArrayList<>();
        OffsetDateTime cursor = alignNextSlot(from.plusMinutes(INTERVAL_MINUTES));
        OffsetDateTime limit = alignNextSlot(to);
        while (!cursor.isAfter(limit)) {
            slots.add(cursor);
            cursor = cursor.plusMinutes(INTERVAL_MINUTES);
        }
        return slots;
    }

    private static OffsetDateTime alignNextSlot(OffsetDateTime t) {
        int minute = t.getMinute();
        int aligned = minute - (minute % INTERVAL_MINUTES);
        return t.withMinute(aligned).withSecond(0).withNano(0);
    }

    private static double baselineTotal(List<SnapshotRow> snapshots, List<Long> pointIds, OffsetDateTime at) {
        Map<Long, Double> last = new HashMap<>();
        for (SnapshotRow row : snapshots) {
            if (row.bucketAt().isAfter(at)) {
                break;
            }
            last.put(row.pointId(), row.value());
        }
        double sum = 0;
        for (Long id : pointIds) {
            Double v = last.get(id);
            if (v != null) {
                sum += v;
            }
        }
        return sum;
    }

    private static List<Double> cumulativeTotalsAt(
            List<SnapshotRow> snapshots,
            List<Long> pointIds,
            List<OffsetDateTime> slotEnds) {
        Map<Long, Double> last = new HashMap<>();
        int snapIdx = 0;
        List<Double> totals = new ArrayList<>(slotEnds.size());

        for (OffsetDateTime slotEnd : slotEnds) {
            while (snapIdx < snapshots.size()
                    && !snapshots.get(snapIdx).bucketAt().isAfter(slotEnd)) {
                SnapshotRow row = snapshots.get(snapIdx++);
                last.put(row.pointId(), row.value());
            }
            double sum = 0;
            for (Long id : pointIds) {
                Double v = last.get(id);
                if (v != null) {
                    sum += v;
                }
            }
            totals.add(sum);
        }
        return totals;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** 누적값 역행·비정상 급증은 0으로 두고 기준선만 재동기화 */
    private static double intervalUsageKwh(double total, double prev) {
        double delta = total - prev;
        if (delta < 0 || delta > MAX_INTERVAL_KWH) {
            return 0;
        }
        return delta;
    }

    private record SnapshotRow(OffsetDateTime bucketAt, long pointId, double value) {
    }
}
