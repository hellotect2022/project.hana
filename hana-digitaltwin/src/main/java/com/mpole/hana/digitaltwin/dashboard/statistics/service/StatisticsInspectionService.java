package com.mpole.hana.digitaltwin.dashboard.statistics.service;

import com.mpole.hana.digitaltwin.dashboard.config.DashboardCacheConfig;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsInspectionItemResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsInspectionResponse;
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
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

/**
 * 통계·리포트 — 설비 점검 현황(더미 UI 호환).
 *
 * <p>점검 Work Order 테이블이 없어, <b>동일 기간·동일 규칙</b>으로 {@code tbl_event} 전체(모의 제외)의
 * 확인(완료)/미확인(진행중)만 집계한다. 기간이 길수록 건수가 줄어들지 않도록 탭마다 필터를 바꾸지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsInspectionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> LABELS = List.of("완료", "진행중", "예정");
    private static final String SOURCE =
            "tbl_event · 기간 내 전체 이벤트 확인(acked_at) — 주⊆월 등 기간 중첩 시 월≥주";

    private final NamedParameterJdbcTemplate jdbc;
    private final StatisticsReportCategoryFilter categoryFilter;

    @Cacheable(
            cacheNames = DashboardCacheConfig.SITUATION_CACHE,
            cacheManager = "dashboardCacheManager",
            key = "'statsInspection:v2:' + #period + ':' + #system")
    public StatisticsInspectionResponse getInspectionStatus(String period, String system) {
        String normalizedPeriod = normalizePeriod(period);
        String normalizedSystem = normalizeSystem(system);
        List<Long> categoryIds = categoryFilter.resolveSmallCategoryIds(normalizedSystem);
        if (categoryIds != null && categoryIds.isEmpty()) {
            return emptyResponse(normalizedPeriod, normalizedSystem);
        }

        OffsetDateTime now = OffsetDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        OffsetDateTime from = rangeFrom(normalizedPeriod, today);

        long[] counts = queryStatusCounts(from, now, categoryIds);
        long completed = counts[0];
        long inProgress = counts[1];
        long total = completed + inProgress;

        List<StatisticsInspectionItemResponse> items = buildItems(completed, inProgress, 0, total);

        return StatisticsInspectionResponse.builder()
                .period(normalizedPeriod)
                .system(normalizedSystem)
                .totalCount(total)
                .linked(total > 0)
                .items(items)
                .source(SOURCE)
                .build();
    }

    private StatisticsInspectionResponse emptyResponse(String period, String system) {
        return StatisticsInspectionResponse.builder()
                .period(period)
                .system(system)
                .totalCount(0)
                .linked(false)
                .items(List.of())
                .source(SOURCE)
                .build();
    }

    /** @return [completed, inProgress] */
    private long[] queryStatusCounts(
            OffsetDateTime from,
            OffsetDateTime to,
            List<Long> categoryIds) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(CASE WHEN e.acked_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS completed,
                       COALESCE(SUM(CASE WHEN e.acked_at IS NULL THEN 1 ELSE 0 END), 0) AS in_progress
                  FROM tbl_event e
                  LEFT JOIN tbl_device d ON d.device_id = e.device_id
                  LEFT JOIN tbl_device_category c ON c.category_id = d.category_id
                 WHERE e.simulated = FALSE
                   AND e.occurred_at >= :from
                   AND e.occurred_at <= :to
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to);

        if (categoryIds != null) {
            sql.append(" AND c.category_id IN (:catIds)");
            params.addValue("catIds", categoryIds);
        }

        List<long[]> rows = jdbc.query(sql.toString(), params, (rs, rowNum) -> new long[]{
                rs.getLong("completed"),
                rs.getLong("in_progress"),
        });
        return rows.isEmpty() ? new long[]{0, 0} : rows.get(0);
    }

    private static List<StatisticsInspectionItemResponse> buildItems(
            long completed,
            long inProgress,
            long scheduled,
            long total) {
        long[] values = {completed, inProgress, scheduled};
        List<StatisticsInspectionItemResponse> items = new ArrayList<>();
        for (int i = 0; i < LABELS.size(); i++) {
            if (values[i] <= 0) {
                continue;
            }
            int pct = total > 0 ? (int) Math.round(values[i] * 100.0 / total) : 0;
            items.add(StatisticsInspectionItemResponse.builder()
                    .name(LABELS.get(i))
                    .value(values[i])
                    .pct(pct)
                    .build());
        }
        return items;
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
