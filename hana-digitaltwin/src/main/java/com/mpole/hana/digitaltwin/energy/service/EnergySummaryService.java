package com.mpole.hana.digitaltwin.energy.service;

import com.mpole.hana.digitaltwin.energy.dto.EnergySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 금일·금월 전력량 계산 (2026-10-12).
 *
 * <pre>
 *   금일 = 현재값 − 오늘 00:00 스냅샷,   금월 = 현재값 − 이달 1일 00:00 스냅샷   (KST)
 *   현재값 = tbl_device_current_value(JSONB), 스냅샷 = tbl_energy_snapshot(5분, event 서버가 기록)
 *   대상 = 카테고리 스키마 accumulate=true 태그의 포인트 (전력감시 PS_PRD_SHV_SHV-3_kWh, 원격검침 계량기 등)
 * </pre>
 *
 * <h3>계산 — 요구사항 식 그대로</h3>
 * {@code 사용량 = 현재값 − 기준값}. 계량기 교체·리셋으로 음수가 되면 0 으로 막는다(음수 사용량은 화면에서 의미가 없다).
 * (2026-10-12 초안은 5분 스냅샷 전부를 이어 증가분을 더했으나, 요구사항과 식이 다르고 금월에 한 달치를 읽어 무거워 단순 뺄셈으로 바꿨다.)
 *
 * <h3>기준값 찾기 — 포인트마다 한 행(PK 인덱스)</h3>
 * <ol>
 *   <li>기간 시작(periodStart) <b>이하</b>의 가장 가까운 스냅샷 — 정상이면 00:00 정각, 빠졌으면 23:55 처럼 그 직전</li>
 *   <li>없으면 기간 시작 <b>이후</b>의 가장 가까운 스냅샷(기간 안 첫 값) — {@code baseAdjusted=true}. 스냅샷을 처음 켠 날·달이 여기 해당하며
 *       그 앞 사용량은 빠진다</li>
 *   <li>스냅샷이 아예 없거나 현재값이 숫자가 아니면 null</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnergySummaryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 이보다 오래 갱신되지 않은 현재값은 stale(통신 끊김 의심) */
    private static final Duration STALE_AFTER = Duration.ofMinutes(15);

    /** 누적 포인트 + 현재값. 장비 필터가 없으면 전체 */
    private static final String TARGET_SQL = """
            SELECT p.point_id, p.device_id, d.device_name, d.device_key, p.tag_name, p.point_display_name,
                   s->>'unit' AS unit,
                   cur.value_raw, cur.update_at
              FROM tbl_device_point p
              JOIN tbl_device d          ON d.device_id = p.device_id
              JOIN tbl_device_category c ON c.category_id = d.category_id
              CROSS JOIN LATERAL jsonb_array_elements(COALESCE(c.schema_definition, '[]'::jsonb)) s
              LEFT JOIN LATERAL (
                    SELECT pt->>'valueRaw' AS value_raw, pt->>'updateDatetime' AS update_at
                      FROM tbl_device_current_value cv
                      CROSS JOIN LATERAL jsonb_array_elements(COALESCE(cv.points, '[]'::jsonb)) pt
                     WHERE cv.device_id = p.device_id AND (pt->>'pointId')::bigint = p.point_id
                     LIMIT 1
              ) cur ON TRUE
             WHERE s->>'tagName' = p.tag_name
               AND (
                    COALESCE((s->>'accumulate')::boolean, false)
                    %s
               )
               %s
             ORDER BY d.device_name, p.tag_name
            """;

    /**
     * 기준값 — 포인트마다 ① 기간 시작 이하 가장 가까운 스냅샷(b), 없으면 ② 이후 가장 가까운 스냅샷(a).
     * 각각 PK(point_id, bucket_at) 인덱스로 한 행만 읽는다(금월도 한 달치를 훑지 않는다).
     * adjusted = ① 이 없어 ② 를 썼다. 둘 다 없으면 base_value·base_at 이 null.
     */
    private static final String BASE_SQL = """
            SELECT p.point_id,
                   COALESCE(b.value, a.value)         AS base_value,
                   COALESCE(b.bucket_at, a.bucket_at) AS base_at,
                   (b.bucket_at IS NULL AND a.bucket_at IS NOT NULL) AS adjusted
              FROM tbl_device_point p
              LEFT JOIN LATERAL (
                    SELECT value, bucket_at FROM tbl_energy_snapshot
                     WHERE point_id = p.point_id AND bucket_at <= :from
                     ORDER BY bucket_at DESC LIMIT 1
              ) b ON TRUE
              LEFT JOIN LATERAL (
                    SELECT value, bucket_at FROM tbl_energy_snapshot
                     WHERE point_id = p.point_id AND bucket_at > :from
                     ORDER BY bucket_at ASC LIMIT 1
              ) a ON TRUE
             WHERE p.point_id IN (:ids)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** @param deviceIds 없거나 비면 전체 누적 포인트 */
    public List<EnergySummary> summary(Collection<Long> deviceIds) {
        return summary(deviceIds, Set.of());
    }

    /**
     * @param forceIncludeTags 스키마 accumulate=false 여도 금일 계산 대상에 포함할 tag_name (예: RM_GM G)
     */
    public List<EnergySummary> summary(Collection<Long> deviceIds, Collection<String> forceIncludeTags) {
        boolean filter = deviceIds != null && !deviceIds.isEmpty();
        Set<String> extraTags = forceIncludeTags == null
                ? Set.of()
                : forceIncludeTags.stream()
                        .filter(t -> t != null && !t.isBlank())
                        .map(String::trim)
                        .collect(java.util.stream.Collectors.toSet());

        String tagExtra = extraTags.isEmpty() ? "" : " OR p.tag_name IN (:forceTags)";
        String deviceFilter = filter ? "AND p.device_id IN (:deviceIds)" : "";

        MapSqlParameterSource params = new MapSqlParameterSource();
        if (filter) params.addValue("deviceIds", deviceIds);
        if (!extraTags.isEmpty()) params.addValue("forceTags", extraTags);

        List<Map<String, Object>> targets = jdbc.queryForList(
                TARGET_SQL.formatted(tagExtra, deviceFilter), params);
        if (targets.isEmpty()) return List.of();

        List<Long> pointIds = targets.stream().map(r -> ((Number) r.get("point_id")).longValue()).toList();
        OffsetDateTime now = OffsetDateTime.now(KST);
        LocalDate today = now.toLocalDate();
        OffsetDateTime dayStart = today.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime monthStart = today.withDayOfMonth(1).atStartOfDay(KST).toOffsetDateTime();

        Map<Long, BaseRow> dayRows = base(pointIds, dayStart);
        Map<Long, BaseRow> weekRows = base(pointIds, weekStart);
        Map<Long, BaseRow> monthRows = base(pointIds, monthStart);

        List<EnergySummary> out = new ArrayList<>(targets.size());
        for (Map<String, Object> r : targets) {
            long pointId = ((Number) r.get("point_id")).longValue();
            Double current = parseNumber((String) r.get("value_raw"));
            OffsetDateTime sourceAt = parseTime((String) r.get("update_at"));
            boolean stale = sourceAt == null || Duration.between(sourceAt, now).compareTo(STALE_AFTER) > 0;
            out.add(new EnergySummary(
                    toLong(r.get("device_id")),
                    (String) r.get("device_name"),
                    (String) r.get("device_key"),
                    pointId,
                    (String) r.get("tag_name"),
                    (String) r.get("point_display_name"),
                    (String) r.get("unit"),
                    current, sourceAt, stale,
                    toPeriod(dayRows.get(pointId), current, dayStart),
                    toPeriod(weekRows.get(pointId), current, weekStart),
                    toPeriod(monthRows.get(pointId), current, monthStart)));
        }
        return out;
    }

    /** 기준 스냅샷. value 가 null 이면 이 포인트는 스냅샷이 아예 없다 */
    private record BaseRow(Double value, OffsetDateTime baseAt, boolean adjusted) {
    }

    private Map<Long, BaseRow> base(List<Long> pointIds, OffsetDateTime from) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", pointIds)
                // OffsetDateTime 그대로 — pgjdbc 가 timestamptz 로 보낸다(Timestamp 는 세션 시간대로 해석돼 KST 경계가 어긋날 수 있음)
                .addValue("from", from);
        Map<Long, BaseRow> map = new HashMap<>();
        jdbc.query(BASE_SQL, params, rs -> {
            double v = rs.getDouble("base_value");
            boolean vNull = rs.wasNull();
            OffsetDateTime at = rs.getObject("base_at", OffsetDateTime.class);
            map.put(rs.getLong("point_id"), new BaseRow(
                    vNull ? null : v,
                    at == null ? null : at.atZoneSameInstant(KST).toOffsetDateTime(),
                    rs.getBoolean("adjusted")));
        });
        return map;
    }

    /** 사용량 = 현재값 − 기준값(음수면 0). 기준값이나 현재값이 없으면 null */
    private static EnergySummary.Period toPeriod(BaseRow row, Double current, OffsetDateTime periodStart) {
        if (row == null || row.value() == null || current == null) {
            return new EnergySummary.Period(null, periodStart,
                    row == null ? null : row.baseAt(), row != null && row.adjusted());
        }
        double used = Math.max(current - row.value(), 0);
        return new EnergySummary.Period(round(used), periodStart, row.baseAt(), row.adjusted());
    }

    /** 부동소수 꼬리(0.30000000000000004) 정리 — 소수 6자리 */
    private static double round(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }

    private static Double parseNumber(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static OffsetDateTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }
}
