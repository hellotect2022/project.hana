package com.mpole.hana.digitaltwin.dashboard.statistics.service;

import com.mpole.hana.digitaltwin.dashboard.config.DashboardCacheConfig;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsEventTypeItemResponse;
import com.mpole.hana.digitaltwin.dashboard.statistics.dto.StatisticsEventTypeResponse;
import com.mpole.hana.digitaltwin.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 통계·리포트 — 이벤트 유형별(심각도 level) 분석.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatisticsEventTypeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> DISPLAY_ORDER = List.of("알람", "경고", "주의", "정보", "기타");

    private final EventRepository eventRepository;

    @Cacheable(
            cacheNames = DashboardCacheConfig.SITUATION_CACHE,
            cacheManager = "dashboardCacheManager",
            key = "'statsEventTypes:' + #period")
    public StatisticsEventTypeResponse getEventTypes(String period) {
        String normalized = normalizePeriod(period);
        OffsetDateTime now = OffsetDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        OffsetDateTime from = rangeFrom(normalized, today);
        OffsetDateTime to = now;

        Map<String, Long> byLabel = new LinkedHashMap<>();
        for (String label : DISPLAY_ORDER) {
            byLabel.put(label, 0L);
        }

        for (Object[] row : eventRepository.countByLevel(from, to)) {
            if (row == null || row.length < 2) {
                continue;
            }
            String levelCode = row[0] == null ? "UNKNOWN" : String.valueOf(row[0]);
            long cnt = ((Number) row[1]).longValue();
            String label = labelForLevel(levelCode);
            byLabel.merge(label, cnt, Long::sum);
        }

        long total = byLabel.values().stream().mapToLong(Long::longValue).sum();
        List<StatisticsEventTypeItemResponse> items = new ArrayList<>();
        for (String label : DISPLAY_ORDER) {
            long value = byLabel.getOrDefault(label, 0L);
            if (value <= 0) {
                continue;
            }
            int pct = total > 0 ? (int) Math.round(value * 100.0 / total) : 0;
            items.add(StatisticsEventTypeItemResponse.builder()
                    .name(label)
                    .value(value)
                    .pct(pct)
                    .levelCode(label)
                    .build());
        }

        return StatisticsEventTypeResponse.builder()
                .period(normalized)
                .totalCount(total)
                .linked(total > 0)
                .items(items)
                .source("tbl_event.level (occurred_at 구간, simulated=false)")
                .build();
    }

    static String labelForLevel(String levelCode) {
        if (levelCode == null || levelCode.isBlank()) {
            return "기타";
        }
        return switch (levelCode.trim().toUpperCase()) {
            case "CRITICAL" -> "알람";
            case "WARNING" -> "경고";
            case "CAUTION" -> "주의";
            case "INFO", "INFORMATION" -> "정보";
            default -> "기타";
        };
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
