package com.mpole.hana.digitaltwin.overview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 종합 현황 계산·검증에 쓰는 조회 모음 (2026-10-16). 모두 읽기 전용 SQL.
 *
 * <pre>
 *   누적 여부  = 장비(또는 분류) 카테고리 schema_definition 에서 같은 tagName 의 "accumulate"
 *               (event 서버 스냅샷 스케줄러와 같은 기준 — true 인 포인트만 tbl_energy_snapshot 에 쌓인다)
 *   현재값     = tbl_device_current_value.points(JSONB) 에서 pointId 가 같은 원소
 *   기준값     = tbl_energy_snapshot — 기간 시작 이하 가장 가까운 값, 없으면 이후 첫 값(adjusted)
 *               (EnergySummaryService 와 같은 규칙)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverviewLookup {

    public record PointInfo(long pointId, String pointKey, String deviceName, boolean accumulate) {
    }

    public record CategoryInfo(boolean exists, String path, boolean hasTag, boolean accumulate, int pointCount) {
    }

    public record InfraInfo(long infraId, String infraName, String infraDisplayName, String refInfraCode,
                            String networkState, OffsetDateTime lastCommAt) {
    }

    public record CurrentValue(Double value, OffsetDateTime updatedAt) {
    }

    public record BaseValue(double value, OffsetDateTime baseAt, boolean adjusted) {
    }

    private static final String POINT_INFO_SQL = """
            SELECT p.point_id, p.point_key, d.device_name, d.device_display_name,
                   COALESCE((s.def->>'accumulate')::boolean, false) AS accumulate
              FROM tbl_device_point p
              JOIN tbl_device d ON d.device_id = p.device_id
              LEFT JOIN tbl_device_category c ON c.category_id = d.category_id
              LEFT JOIN LATERAL (
                    SELECT x AS def
                      FROM jsonb_array_elements(COALESCE(c.schema_definition, '[]'::jsonb)) x
                     WHERE x->>'tagName' = p.tag_name
                     LIMIT 1
              ) s ON TRUE
             WHERE p.point_id IN (:ids)
            """;

    private static final String CATEGORY_INFO_SQL = """
            WITH RECURSIVE up AS (
                SELECT category_id, category_parent_id, category_name, 1 AS lvl
                  FROM tbl_device_category WHERE category_id = :cid
                UNION ALL
                SELECT c.category_id, c.category_parent_id, c.category_name, up.lvl + 1
                  FROM tbl_device_category c JOIN up ON c.category_id = up.category_parent_id
                 WHERE up.lvl < 10
            )
            SELECT EXISTS (SELECT 1 FROM tbl_device_category WHERE category_id = :cid)       AS cat_exists,
                   (SELECT string_agg(category_name, ' › ' ORDER BY lvl DESC) FROM up)      AS path,
                   COALESCE(def.has_tag, false)                                             AS has_tag,
                   COALESCE(def.accumulate, false)                                          AS accumulate,
                   (SELECT count(*) FROM tbl_device_point p JOIN tbl_device d ON d.device_id = p.device_id
                     WHERE d.category_id = :cid AND p.tag_name = :tag)                       AS point_count
              FROM (SELECT 1) one
              LEFT JOIN LATERAL (
                    SELECT true AS has_tag, COALESCE((x->>'accumulate')::boolean, false) AS accumulate
                      FROM tbl_device_category c
                      CROSS JOIN LATERAL jsonb_array_elements(COALESCE(c.schema_definition, '[]'::jsonb)) x
                     WHERE c.category_id = :cid AND x->>'tagName' = :tag
                     LIMIT 1
              ) def ON TRUE
            """;

    private static final String CATEGORY_POINTS_SQL = """
            SELECT p.point_id
              FROM tbl_device_point p
              JOIN tbl_device d ON d.device_id = p.device_id
             WHERE d.category_id = :cid AND p.tag_name = :tag
            """;

    private static final String INFRA_SQL = """
            SELECT infra_id, infra_name, infra_display_name, ref_infra_code, network_state, last_comm_at
              FROM tbl_device_infra
             WHERE infra_id IN (:ids)
            """;

    private static final String CURRENT_SQL = """
            SELECT p.point_id, cur.value_raw, cur.update_at
              FROM tbl_device_point p
              LEFT JOIN LATERAL (
                    SELECT pt->>'valueRaw' AS value_raw, pt->>'updateDatetime' AS update_at
                      FROM tbl_device_current_value cv
                      CROSS JOIN LATERAL jsonb_array_elements(COALESCE(cv.points, '[]'::jsonb)) pt
                     WHERE cv.device_id = p.device_id AND (pt->>'pointId')::bigint = p.point_id
                     LIMIT 1
              ) cur ON TRUE
             WHERE p.point_id IN (:ids)
            """;

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

    public Map<Long, PointInfo> pointInfo(Collection<Long> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) return Map.of();
        Map<Long, PointInfo> out = new HashMap<>();
        jdbc.query(POINT_INFO_SQL, new MapSqlParameterSource("ids", pointIds), rs -> {
            String display = rs.getString("device_display_name");
            out.put(rs.getLong("point_id"), new PointInfo(
                    rs.getLong("point_id"),
                    rs.getString("point_key"),
                    display != null && !display.isBlank() ? display : rs.getString("device_name"),
                    rs.getBoolean("accumulate")));
        });
        return out;
    }

    public CategoryInfo categoryInfo(long categoryId, String tagName) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("cid", categoryId).addValue("tag", tagName);
        return jdbc.queryForObject(CATEGORY_INFO_SQL, p, (rs, i) -> new CategoryInfo(
                rs.getBoolean("cat_exists"),
                rs.getString("path"),
                rs.getBoolean("has_tag"),
                rs.getBoolean("accumulate"),
                rs.getInt("point_count")));
    }

    public List<Long> categoryPointIds(long categoryId, String tagName) {
        return jdbc.queryForList(CATEGORY_POINTS_SQL,
                new MapSqlParameterSource().addValue("cid", categoryId).addValue("tag", tagName), Long.class);
    }

    public Map<Long, InfraInfo> infraInfo(Collection<Long> infraIds) {
        if (infraIds == null || infraIds.isEmpty()) return Map.of();
        Map<Long, InfraInfo> out = new HashMap<>();
        jdbc.query(INFRA_SQL, new MapSqlParameterSource("ids", infraIds), rs -> {
            out.put(rs.getLong("infra_id"), new InfraInfo(
                    rs.getLong("infra_id"),
                    rs.getString("infra_name"),
                    rs.getString("infra_display_name"),
                    rs.getString("ref_infra_code"),
                    rs.getString("network_state"),
                    rs.getObject("last_comm_at", OffsetDateTime.class)));
        });
        return out;
    }

    /** 현재값 — 숫자가 아니면 value=null */
    public Map<Long, CurrentValue> currentValues(Collection<Long> pointIds) {
        if (pointIds == null || pointIds.isEmpty()) return Map.of();
        Map<Long, CurrentValue> out = new HashMap<>();
        jdbc.query(CURRENT_SQL, new MapSqlParameterSource("ids", pointIds), rs -> {
            out.put(rs.getLong("point_id"), new CurrentValue(
                    parseDouble(rs.getString("value_raw")),
                    parseTime(rs.getString("update_at"))));
        });
        return out;
    }

    /** 기간 시작 기준값 — 스냅샷이 아예 없는 포인트는 결과에 없다 */
    public Map<Long, BaseValue> baseValues(Collection<Long> pointIds, OffsetDateTime from) {
        if (pointIds == null || pointIds.isEmpty()) return Map.of();
        Map<Long, BaseValue> out = new HashMap<>();
        jdbc.query(BASE_SQL, new MapSqlParameterSource().addValue("ids", pointIds).addValue("from", from), rs -> {
            Object v = rs.getObject("base_value");
            if (v == null) return;
            out.put(rs.getLong("point_id"), new BaseValue(
                    ((Number) v).doubleValue(),
                    rs.getObject("base_at", OffsetDateTime.class),
                    rs.getBoolean("adjusted")));
        });
        return out;
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.valueOf(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static OffsetDateTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
