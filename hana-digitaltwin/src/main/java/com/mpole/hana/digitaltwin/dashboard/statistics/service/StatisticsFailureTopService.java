package com.mpole.hana.digitaltwin.dashboard.statistics.service;

import com.mpole.hana.digitaltwin.dashboard.config.DashboardCacheConfig;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsFailureTopItemResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsFailureTopResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.support.StatisticsReportCategoryFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

/**
 * 통계·리포트 — 설비 장애 TOP (tbl_event 장비별 발생 건수).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsFailureTopService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter LAST_OCCURRED = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final int DEFAULT_MODAL_LIMIT = 50;

    private final NamedParameterJdbcTemplate jdbc;
    private final StatisticsReportCategoryFilter categoryFilter;

    @Cacheable(
            cacheNames = DashboardCacheConfig.SITUATION_CACHE,
            cacheManager = "dashboardCacheManager",
            key = "'statsFailureTop:' + #period + ':' + #system")
    public StatisticsFailureTopResponse getFailureTop(String period, String system) {
        return getFailureTop(period, system, DEFAULT_MODAL_LIMIT);
    }

    public StatisticsFailureTopResponse getFailureTop(String period, String system, int limit) {
        String normalizedPeriod = normalizePeriod(period);
        String normalizedSystem = normalizeSystem(system);
        List<Long> categoryIds = categoryFilter.resolveSmallCategoryIds(normalizedSystem);
        if (categoryIds != null && categoryIds.isEmpty()) {
            return emptyResponse(normalizedPeriod, normalizedSystem);
        }

        OffsetDateTime now = OffsetDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        OffsetDateTime from = rangeFrom(normalizedPeriod, today);

        int effectiveLimit = Math.min(Math.max(limit, 10), 100);
        List<StatisticsFailureTopItemResponse> rows = queryTop(from, now, categoryIds, effectiveLimit);
        assignRanks(rows);

        List<StatisticsFailureTopItemResponse> top10 = rows.size() <= 10
                ? rows
                : new ArrayList<>(rows.subList(0, 10));

        boolean linked = !rows.isEmpty();

        return StatisticsFailureTopResponse.builder()
                .period(normalizedPeriod)
                .system(normalizedSystem)
                .linked(linked)
                .top10(top10)
                .rows(rows)
                .source("tbl_event · device_id별 occurred_at 집계(simulated=false)")
                .build();
    }

    private StatisticsFailureTopResponse emptyResponse(String period, String system) {
        return StatisticsFailureTopResponse.builder()
                .period(period)
                .system(system)
                .linked(false)
                .top10(List.of())
                .rows(List.of())
                .source("tbl_event · device_id별 occurred_at 집계(simulated=false)")
                .build();
    }

    private List<StatisticsFailureTopItemResponse> queryTop(
            OffsetDateTime from,
            OffsetDateTime to,
            List<Long> categoryIds,
            int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.device_id,
                       COALESCE(d.device_name, e.device_key, '—') AS device_name,
                       c.category_code,
                       COALESCE(c.category_name, '—') AS category_name,
                       COALESCE(f.floor_name, z.zone_name, '—') AS location_name,
                       COUNT(*) AS cnt,
                       MAX(e.occurred_at) AS last_at
                  FROM tbl_event e
                  LEFT JOIN tbl_device d ON d.device_id = e.device_id
                  LEFT JOIN tbl_device_category c ON c.category_id = d.category_id
                  LEFT JOIN tbl_device_placement p ON p.device_id = d.device_id
                  LEFT JOIN tbl_unity_loc_zone z ON z.zone_id = p.unity_zone_id
                  LEFT JOIN tbl_unity_loc_floor f ON f.floor_id = z.floor_id
                 WHERE e.simulated = FALSE
                   AND e.device_id IS NOT NULL
                   AND e.occurred_at >= :from
                   AND e.occurred_at <= :to
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to)
                .addValue("lim", limit);

        if (categoryIds != null) {
            sql.append(" AND c.category_id IN (:catIds)");
            params.addValue("catIds", categoryIds);
        }

        sql.append("""
                 GROUP BY e.device_id, d.device_name, e.device_key, c.category_code, c.category_name,
                          f.floor_name, z.zone_name
                 ORDER BY cnt DESC
                 LIMIT :lim
                """);

        return jdbc.query(sql.toString(), params, (rs, rowNum) -> {
            long deviceId = rs.getLong("device_id");
            String deviceName = rs.getString("device_name");
            String categoryCode = rs.getString("category_code");
            String categoryName = rs.getString("category_name");
            String location = rs.getString("location_name");
            long count = rs.getLong("cnt");
            OffsetDateTime lastAt = rs.getObject("last_at", OffsetDateTime.class);
            String lastLabel = lastAt == null
                    ? "—"
                    : lastAt.atZoneSameInstant(KST).format(LAST_OCCURRED);

            return StatisticsFailureTopItemResponse.builder()
                    .id(String.valueOf(deviceId))
                    .rank(0)
                    .name(deviceName)
                    .count(count)
                    .system(StatisticsReportCategoryFilter.systemLabelFromCategory(categoryCode, categoryName))
                    .location(location)
                    .lastOccurred(lastLabel)
                    .build();
        });
    }

    private static void assignRanks(List<StatisticsFailureTopItemResponse> rows) {
        for (int i = 0; i < rows.size(); i++) {
            StatisticsFailureTopItemResponse row = rows.get(i);
            rows.set(i, StatisticsFailureTopItemResponse.builder()
                    .id(row.id())
                    .rank(i + 1)
                    .name(row.name())
                    .count(row.count())
                    .system(row.system())
                    .location(row.location())
                    .lastOccurred(row.lastOccurred())
                    .build());
        }
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

    private static String normalizeSystem(String system) {
        if (system == null || system.isBlank()) {
            return "all";
        }
        return system.trim().toLowerCase();
    }

    private static OffsetDateTime rangeFrom(String period, LocalDate today) {
        return switch (period) {
            case "day" -> today.atStartOfDay(KST).toOffsetDateTime();
            case "week" -> today.minusDays(6).atStartOfDay(KST).toOffsetDateTime();
            case "month" -> today.withDayOfMonth(1).atStartOfDay(KST).toOffsetDateTime();
            case "quarter" -> {
                int q = today.get(IsoFields.QUARTER_OF_YEAR);
                int month = (q - 1) * 3 + 1;
                yield LocalDate.of(today.getYear(), month, 1).atStartOfDay(KST).toOffsetDateTime();
            }
            case "year" -> LocalDate.of(today.getYear(), 1, 1).atStartOfDay(KST).toOffsetDateTime();
            default -> today.withDayOfMonth(1).atStartOfDay(KST).toOffsetDateTime();
        };
    }
}
