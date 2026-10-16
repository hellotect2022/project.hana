package com.mpole.hana.digitaltwin.dashboard.statistics.service;

import com.mpole.hana.digitaltwin.dashboard.config.DashboardCacheConfig;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsEventTrendPointResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsEventTrendResponse;
import com.mpole.hana.digitaltwin.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
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
 * 통계·리포트 — 이벤트 발생 추이 (tbl_event occurred_at, simulated=false).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsEventTrendService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("MM/dd");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MM'월'");
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:00");

    private final EventRepository eventRepository;

    @Cacheable(
            cacheNames = DashboardCacheConfig.SITUATION_CACHE,
            cacheManager = "dashboardCacheManager",
            key = "'statsEventTrend:' + #period")
    public StatisticsEventTrendResponse getEventTrend(String period) {
        String normalized = normalizePeriod(period);
        OffsetDateTime now = OffsetDateTime.now(KST);
        LocalDate today = now.toLocalDate();

        List<StatisticsEventTrendPointResponse> points = buildPoints(normalized, today, now);
        long total = points.stream().mapToLong(StatisticsEventTrendPointResponse::count).sum();
        boolean linked = total > 0;

        return StatisticsEventTrendResponse.builder()
                .period(normalized)
                .points(points)
                .linked(linked)
                .totalCount(total)
                .source("tbl_event (occurred_at, simulated=false)")
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

    private List<StatisticsEventTrendPointResponse> buildPoints(
            String period,
            LocalDate today,
            OffsetDateTime now) {
        return switch (period) {
            case "day" -> buildHourlyToday(today, now);
            case "week" -> buildDailyRange(today.minusDays(6), today, now);
            case "month" -> buildDailyRange(today.withDayOfMonth(1), today, now);
            case "quarter" -> buildYearQuarters(today, now);
            case "year" -> buildYearMonths(today, now);
            default -> buildDailyRange(today.withDayOfMonth(1), today, now);
        };
    }

    private List<StatisticsEventTrendPointResponse> buildHourlyToday(LocalDate today, OffsetDateTime now) {
        OffsetDateTime from = today.atStartOfDay(KST).toOffsetDateTime();
        Map<Integer, Long> counts = toHourMap(eventRepository.countHourlyOccurrences(from, now));

        List<StatisticsEventTrendPointResponse> out = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            OffsetDateTime hourStart = today.atTime(h, 0).atZone(KST).toOffsetDateTime();
            if (hourStart.isAfter(now)) {
                break;
            }
            out.add(StatisticsEventTrendPointResponse.builder()
                    .date(hourStart.format(HOUR_LABEL))
                    .count(counts.getOrDefault(h, 0L))
                    .build());
        }
        return out;
    }

    private List<StatisticsEventTrendPointResponse> buildDailyRange(
            LocalDate fromDay,
            LocalDate toDay,
            OffsetDateTime now) {
        OffsetDateTime from = fromDay.atStartOfDay(KST).toOffsetDateTime();
        Map<LocalDate, Long> counts = toDayMap(eventRepository.countDailyOccurrences(from, now));

        List<StatisticsEventTrendPointResponse> out = new ArrayList<>();
        for (LocalDate d = fromDay; !d.isAfter(toDay); d = d.plusDays(1)) {
            if (d.isAfter(now.toLocalDate())) {
                break;
            }
            out.add(StatisticsEventTrendPointResponse.builder()
                    .date(d.format(DATE_LABEL))
                    .count(counts.getOrDefault(d, 0L))
                    .build());
        }
        return out;
    }

    /** 올해 1~4분기별 이벤트 건수 */
    private List<StatisticsEventTrendPointResponse> buildYearQuarters(LocalDate today, OffsetDateTime now) {
        int year = today.getYear();
        OffsetDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay(KST).toOffsetDateTime();
        Map<LocalDate, Long> monthCounts = toMonthStartMap(
                eventRepository.countMonthlyOccurrences(yearStart, now));

        List<StatisticsEventTrendPointResponse> out = new ArrayList<>();
        for (int q = 1; q <= 4; q++) {
            int monthStart = (q - 1) * 3 + 1;
            LocalDate qStart = LocalDate.of(year, monthStart, 1);
            if (qStart.isAfter(today)) {
                out.add(StatisticsEventTrendPointResponse.builder()
                        .date(q + "분기")
                        .count(0L)
                        .build());
                continue;
            }
            long sum = 0;
            for (int m = 0; m < 3; m++) {
                LocalDate mStart = qStart.plusMonths(m);
                if (!mStart.isAfter(today)) {
                    sum += monthCounts.getOrDefault(mStart, 0L);
                }
            }
            out.add(StatisticsEventTrendPointResponse.builder()
                    .date(q + "분기")
                    .count(sum)
                    .build());
        }
        return out;
    }

    private List<StatisticsEventTrendPointResponse> buildYearMonths(LocalDate today, OffsetDateTime now) {
        int year = today.getYear();
        OffsetDateTime yearStart = LocalDate.of(year, 1, 1).atStartOfDay(KST).toOffsetDateTime();
        Map<LocalDate, Long> monthCounts = toMonthStartMap(
                eventRepository.countMonthlyOccurrences(yearStart, now));

        List<StatisticsEventTrendPointResponse> out = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            LocalDate mStart = LocalDate.of(year, m, 1);
            if (mStart.isAfter(today)) {
                out.add(StatisticsEventTrendPointResponse.builder()
                        .date(mStart.format(MONTH_LABEL))
                        .count(0L)
                        .build());
                continue;
            }
            out.add(StatisticsEventTrendPointResponse.builder()
                    .date(mStart.format(MONTH_LABEL))
                    .count(monthCounts.getOrDefault(mStart, 0L))
                    .build());
        }
        return out;
    }

    private long sumDailyCounts(LocalDate fromDay, LocalDate toDay, OffsetDateTime now) {
        OffsetDateTime from = fromDay.atStartOfDay(KST).toOffsetDateTime();
        Map<LocalDate, Long> counts = toDayMap(eventRepository.countDailyOccurrences(from, now));
        long sum = 0;
        for (LocalDate d = fromDay; !d.isAfter(toDay); d = d.plusDays(1)) {
            sum += counts.getOrDefault(d, 0L);
        }
        return sum;
    }

    private static Map<Integer, Long> toHourMap(List<Object[]> rows) {
        Map<Integer, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            int hour = ((Number) row[0]).intValue();
            long cnt = ((Number) row[1]).longValue();
            map.put(hour, cnt);
        }
        return map;
    }

    private static Map<LocalDate, Long> toDayMap(List<Object[]> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            LocalDate day = toLocalDate(row[0]);
            if (day == null) {
                continue;
            }
            long cnt = ((Number) row[1]).longValue();
            map.put(day, cnt);
        }
        return map;
    }

    private static Map<LocalDate, Long> toMonthStartMap(List<Object[]> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            LocalDate day = toLocalDate(row[0]);
            if (day == null) {
                continue;
            }
            LocalDate monthStart = LocalDate.of(day.getYear(), day.getMonth(), 1);
            long cnt = ((Number) row[1]).longValue();
            map.put(monthStart, cnt);
        }
        return map;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atZone(KST).toLocalDate();
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atZone(KST).toLocalDate();
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.atZoneSameInstant(KST).toLocalDate();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.atZone(KST).toLocalDate();
        }
        return null;
    }
}
