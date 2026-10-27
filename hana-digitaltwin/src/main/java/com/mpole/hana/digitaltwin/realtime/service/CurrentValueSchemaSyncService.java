package com.mpole.hana.digitaltwin.realtime.service;

import com.mpole.hana.digitaltwin.realtime.dto.CurrentValueSchemaSyncResponse;
import com.mpole.hana.digitaltwin.realtime.dto.CurrentValueSchemaSyncResponse.RemovedTag;
import com.mpole.hana.digitaltwin.realtime.dto.PointSchemaSyncResponse;
import com.mpole.hana.digitaltwin.realtime.dto.PointSchemaSyncResponse.OrphanPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 현재값 스키마 정리 (2026-10-26) — {@code tbl_device_current_value.points} 에서 장비 카테고리 스키마에 없는 태그를 지운다.
 *
 * <p>event 서버 현재값 기록을 태그 단위 병합(write-mode=MERGE)으로 바꾸면, 스키마에서 지운 태그가 points 에 계속 남는다
 * (통째 교체일 때는 다음 수신에 자연히 빠졌다). 관리자 화면(장비 카테고리 관리 › 현재값 스키마 동기화)에서 실행한다.</p>
 *
 * <ul>
 *   <li>스키마가 비어 있거나 없는 카테고리의 장비는 건드리지 않는다 — 스키마 미설정을 "태그 전부 삭제"로 보지 않는다.</li>
 *   <li>카테고리가 없는 장비, points 가 배열이 아닌 행도 건드리지 않는다.</li>
 *   <li>태그명은 앞뒤 공백을 무시하고 비교한다(스키마 인덱스 PointSchemaIndex 와 같은 규칙). 태그명이 없는 원소는 지운다.</li>
 *   <li>{@code updated_at}·{@code connection_state} 는 바꾸지 않는다 — 값 신선도·통신 상태 의미라서.</li>
 *   <li>UPDATE 한 문장이라 행 단위로 원자적이다. 동시에 들어오는 event 서버 병합 기록과는 행 잠금으로 순서가 정해진다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentValueSchemaSyncService {

    /** 카테고리별 스키마 태그 목록(스키마가 있는 카테고리만) */
    private static final String SCHEMA_CTE = """
            WITH sch AS (
                SELECT c.category_id, c.category_name, array_agg(btrim(s->>'tagName')) AS tags
                  FROM tbl_device_category c
                 CROSS JOIN LATERAL jsonb_array_elements(c.schema_definition) s
                 WHERE jsonb_typeof(c.schema_definition) = 'array'
                   AND jsonb_array_length(c.schema_definition) > 0
                   AND NULLIF(btrim(s->>'tagName'), '') IS NOT NULL
                 GROUP BY c.category_id, c.category_name)
            """;

    private static final String PREVIEW_SQL = SCHEMA_CTE + """
            SELECT sch.category_id, sch.category_name, e->>'tagName' AS tag_name,
                   count(DISTINCT cv.device_id) AS device_count
              FROM tbl_device_current_value cv
              JOIN tbl_device d ON d.device_id = cv.device_id
              JOIN sch ON sch.category_id = d.category_id
             CROSS JOIN LATERAL jsonb_array_elements(
                       CASE WHEN jsonb_typeof(cv.points) = 'array' THEN cv.points ELSE '[]'::jsonb END) e
             WHERE NOT (COALESCE(btrim(e->>'tagName'), '') = ANY(sch.tags))
             GROUP BY sch.category_id, sch.category_name, e->>'tagName'
             ORDER BY device_count DESC, sch.category_name, tag_name
            """;

    private static final String UPDATE_SQL = SCHEMA_CTE + """
            UPDATE tbl_device_current_value cv
               SET points = COALESCE((SELECT jsonb_agg(x.e ORDER BY x.ord)
                                        FROM jsonb_array_elements(cv.points) WITH ORDINALITY AS x(e, ord)
                                       WHERE COALESCE(btrim(x.e->>'tagName'), '') = ANY(sch.tags)), '[]'::jsonb)
              FROM tbl_device d
              JOIN sch ON sch.category_id = d.category_id
             WHERE cv.device_id = d.device_id
               AND jsonb_typeof(cv.points) = 'array'
               AND EXISTS (SELECT 1 FROM jsonb_array_elements(cv.points) e
                            WHERE NOT (COALESCE(btrim(e->>'tagName'), '') = ANY(sch.tags)))
            """;

    /* ── 등록 포인트(tbl_device_point) 정리 — 2026-10-26 ──
     * 스키마 저장 시 DevicePointSchemaSyncService 가 "이전 스키마 대비" 빠진 태그를 지우지만, 스키마를 거치지 않고 생긴 포인트
     * (장비별 수동 추가·기능 도입 전 데이터)는 남는다. 여기서는 "지금 스키마" 기준으로 다시 맞춘다.
     * 등록 포인트가 남아 있으면 gateway 가 그 태그를 계속 발행해 현재값에서 지워도 다시 생긴다. */

    private static final String ORPHAN_POINTS_SQL = SCHEMA_CTE + """
            SELECT p.point_id, p.device_id, d.device_name, d.category_id, c.category_name, p.tag_name, p.point_key,
                   (SELECT count(*) FROM tbl_device_command_point cp WHERE cp.point_id = p.point_id) AS command_point_count,
                   (SELECT count(*) FROM tbl_event e WHERE e.point_id = p.point_id)                  AS event_count
              FROM tbl_device_point p
              JOIN tbl_device d          ON d.device_id = p.device_id
              JOIN sch                   ON sch.category_id = d.category_id
              JOIN tbl_device_category c ON c.category_id = d.category_id
             WHERE NOT (COALESCE(btrim(p.tag_name), '') = ANY(sch.tags))
             ORDER BY c.category_name, d.device_name, p.tag_name
            """;

    /** 제어 포인트(FK)로 쓰는 포인트는 건너뛴다 */
    private static final String DELETE_ORPHAN_POINTS_SQL = SCHEMA_CTE + """
            DELETE FROM tbl_device_point p
             USING tbl_device d, sch
             WHERE d.device_id = p.device_id
               AND sch.category_id = d.category_id
               AND NOT (COALESCE(btrim(p.tag_name), '') = ANY(sch.tags))
               AND NOT EXISTS (SELECT 1 FROM tbl_device_command_point cp WHERE cp.point_id = p.point_id)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /** 스키마에 없는 등록 포인트 미리보기 — 아무것도 바꾸지 않는다 */
    @Transactional(readOnly = true)
    public PointSchemaSyncResponse previewPoints() {
        List<OrphanPoint> points = findOrphanPoints();
        return new PointSchemaSyncResponse(false, countDeletable(points), points.size() - countDeletable(points), points, null);
    }

    /**
     * 스키마에 없는 등록 포인트를 지우고, 같은 트랜잭션에서 현재값도 정리한다.
     * 응답의 points 는 지우기 직전 기준. gateway 는 등록 포인트를 주기적으로(기본 5분) 다시 읽으므로
     * 그 전까지 들어온 값으로 현재값에 태그가 다시 생길 수 있다 — 그때는 현재값 동기화를 한 번 더 실행한다.
     */
    @Transactional
    public PointSchemaSyncResponse syncPoints() {
        List<OrphanPoint> points = findOrphanPoints();
        int deleted = points.isEmpty() ? 0 : jdbc.update(DELETE_ORPHAN_POINTS_SQL, new MapSqlParameterSource());
        CurrentValueSchemaSyncResponse currentValue = sync();
        log.info("[PointSchemaSync] 등록 포인트 {}개 삭제, 제어 포인트 사용으로 건너뜀 {}개",
                deleted, points.size() - countDeletable(points));
        return new PointSchemaSyncResponse(true, deleted, points.size() - countDeletable(points), points, currentValue);
    }

    private List<OrphanPoint> findOrphanPoints() {
        return jdbc.query(ORPHAN_POINTS_SQL, new MapSqlParameterSource(), (rs, i) -> {
            int cmd = rs.getInt("command_point_count");
            return new OrphanPoint(
                    rs.getLong("point_id"),
                    rs.getLong("device_id"),
                    rs.getString("device_name"),
                    rs.getLong("category_id"),
                    rs.getString("category_name"),
                    rs.getString("tag_name"),
                    rs.getString("point_key"),
                    cmd,
                    rs.getInt("event_count"),
                    cmd == 0);
        });
    }

    private static int countDeletable(List<OrphanPoint> points) {
        return (int) points.stream().filter(OrphanPoint::deletable).count();
    }

    /** 지울 태그 미리보기 — 아무것도 바꾸지 않는다 */
    @Transactional(readOnly = true)
    public CurrentValueSchemaSyncResponse preview() {
        return build(findRemovable(), false, 0);
    }

    /** 스키마에 없는 태그를 지운다. 응답의 removedTags 는 지우기 직전 기준 */
    @Transactional
    public CurrentValueSchemaSyncResponse sync() {
        List<RemovedTag> removable = findRemovable();
        int updated = removable.isEmpty() ? 0 : jdbc.update(UPDATE_SQL, new MapSqlParameterSource());
        log.info("[CurrentValueSchemaSync] 장비 {}대 정리, 지운 태그 종류 {}건", updated, removable.size());
        return build(removable, true, updated);
    }

    private List<RemovedTag> findRemovable() {
        return jdbc.query(PREVIEW_SQL, new MapSqlParameterSource(), (rs, i) -> new RemovedTag(
                rs.getLong("category_id"),
                rs.getString("category_name"),
                rs.getString("tag_name"),
                rs.getInt("device_count")));
    }

    private static CurrentValueSchemaSyncResponse build(List<RemovedTag> tags, boolean applied, int updatedDevices) {
        int pointCount = tags.stream().mapToInt(RemovedTag::deviceCount).sum();
        return new CurrentValueSchemaSyncResponse(applied, updatedDevices, pointCount, tags);
    }
}
