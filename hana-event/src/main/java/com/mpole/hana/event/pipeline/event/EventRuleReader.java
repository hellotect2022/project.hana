package com.mpole.hana.event.pipeline.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.event.pipeline.event.dto.ActiveEventRow;
import com.mpole.hana.event.pipeline.event.dto.CategoryRow;
import com.mpole.hana.event.pipeline.event.dto.RuleLevelRow;
import com.mpole.hana.event.pipeline.event.dto.RuleRow;
import io.r2dbc.spi.Parameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 이벤트 규칙/디바이스-카테고리/카테고리 트리를 R2DBC(DatabaseClient)로 조회한다.
 *
 * <p>CurrentValueService 와 동일하게 스키마 한정 없이 unqualified 테이블명(core 스키마는
 * connection factory 의 schema 옵션으로 지정)으로 조회하며, 모든 반환은 Mono/Flux 이므로
 * 블로킹이 발생하지 않는다.</p>
 *
 * <p>digitaltwin 의 enum 에 의존하지 않는다. operator/level/scope 등은
 * DB 에 enum name 문자열(예: "GTE", "EQ", "CRITICAL", "CATEGORY")로 저장되어 있으며
 * 여기서는 String 그대로 읽어 상위 계층(Cache/Evaluator)에 전달한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventRuleReader {

    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;

    /**
     * active=true 인 규칙 목록을 조회한다.
     */
    public Flux<RuleRow> loadActiveRules() {
        String sql = """
                SELECT rule_id, rule_name, event_code, scope, category_id, device_id,
                       tag_name, point_type, operator,
                       sustain_sec, deadband, suppress_mode
                FROM tbl_event_rule
                WHERE active = true
                """;
        return postgresDatabaseClient.sql(sql)
                .map((row, meta) -> new RuleRow(
                        row.get("rule_id", Long.class),
                        row.get("rule_name", String.class),
                        row.get("event_code", String.class),
                        row.get("scope", String.class),
                        row.get("category_id", Long.class),
                        row.get("device_id", Long.class),
                        row.get("tag_name", String.class),
                        row.get("point_type", String.class),
                        row.get("operator", String.class),
                        toInt(row.get("sustain_sec", Integer.class)),
                        row.get("deadband", Double.class),
                        row.get("suppress_mode", String.class)
                ))
                .all()
                .doOnError(e -> log.error("[EventRuleReader] loadActiveRules 실패: {}", e.getMessage()));
    }

    /**
     * active=true 규칙에 속한 레벨을 ruleId 기준으로 그룹핑해 반환한다.
     * (2쿼리 방식: 규칙 로드와 별개로 레벨을 한 번에 로드 후 in-memory group)
     */
    public Mono<Map<Long, List<RuleLevelRow>>> loadLevelsByRuleId() {
        String sql = """
                SELECT l.level_id, l.rule_id, l.level, l.threshold_value, l.trigger_value,
                       l.icon, l.effect, l.display_order, l.outputs
                FROM tbl_event_rule_level l
                JOIN tbl_event_rule r ON r.rule_id = l.rule_id
                WHERE r.active = true
                ORDER BY l.rule_id, l.display_order
                """;
        return postgresDatabaseClient.sql(sql)
                .map((row, meta) -> new RuleLevelRow(
                        row.get("level_id", Long.class),
                        row.get("rule_id", Long.class),
                        row.get("level", String.class),
                        row.get("threshold_value", Double.class),
                        row.get("trigger_value", String.class),
                        row.get("icon", String.class),
                        row.get("effect", String.class),
                        toInt(row.get("display_order", Integer.class)),
                        parseOutputs(row.get("outputs", String.class))
                ))
                .all()
                .collect(Collectors.groupingBy(RuleLevelRow::ruleId))
                .doOnError(e -> log.error("[EventRuleReader] loadLevelsByRuleId 실패: {}", e.getMessage()));
    }

    /**
     * deviceId → categoryId 매핑. 여기서 categoryId 는 tbl_device 에 직접 저장된 값이며,
     * 설계상 device 는 소분류(depth=2)에 매핑된 것으로 간주한다.
     */
    public Mono<Map<Long, Long>> loadDeviceCategoryMap() {
        String sql = "SELECT device_id, category_id FROM tbl_device";
        return postgresDatabaseClient.sql(sql)
                .map((row, meta) -> Map.entry(
                        row.get("device_id", Long.class),
                        row.get("category_id", Long.class)
                ))
                .all()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .doOnError(e -> log.error("[EventRuleReader] loadDeviceCategoryMap 실패: {}", e.getMessage()));
    }

    /**
     * deviceId → alarm_state(장비 알람 마스터 스위치) 맵. false 인 장비는 평가기에서 이벤트를 억제한다.
     */
    public Mono<Map<Long, Boolean>> loadDeviceAlarmEnabled() {
        String sql = "SELECT device_id, alarm_state FROM tbl_device";
        return postgresDatabaseClient.sql(sql)
                .map((row, meta) -> {
                    Boolean v = row.get("alarm_state", Boolean.class);
                    return Map.entry(row.get("device_id", Long.class), v == null ? Boolean.TRUE : v);
                })
                .all()
                .filter(e -> e.getKey() != null)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .doOnError(e -> log.error("[EventRuleReader] loadDeviceAlarmEnabled 실패: {}", e.getMessage()));
    }

    /**
     * 카테고리 트리(부모/깊이) 전체 노드. CATEGORY 규칙을 하위 소분류로 전개할 때 사용.
     */
    public Flux<CategoryRow> loadCategoryTree() {
        String sql = "SELECT category_id, category_parent_id, depth FROM tbl_device_category";
        return postgresDatabaseClient.sql(sql)
                .map((row, meta) -> new CategoryRow(
                        row.get("category_id", Long.class),
                        row.get("category_parent_id", Long.class),
                        row.get("depth", Integer.class)
                ))
                .all()
                .doOnError(e -> log.error("[EventRuleReader] loadCategoryTree 실패: {}", e.getMessage()));
    }

    private static final String ACTIVE_ROW_SELECT = """
            SELECT e.event_id, e.rule_id, e.device_id, e.point_id, e.level, e.occurred_at, e.cleared_at, e.acked_at,
                   e.rule_name, e.event_code, e.device_key, e.tag_name, e.last_occurred_at,
                   p.point_display_name
              FROM tbl_event e
              LEFT JOIN tbl_event_rule r ON r.rule_id = e.rule_id
              LEFT JOIN tbl_device_point p ON p.point_id = e.point_id
            """;

    /**
     * 기동 시 활성 맵 복원 — <b>미확인</b>인 상태형 이벤트 전부(2026-10-02: 확인 = 종료이므로 "맵에 있다 = 미확인").
     *
     * <p>미해소만 복원하면 "해소됐지만 미확인" 이벤트의 억제가 재기동으로 풀려 확인 없이 다시 울린다.
     * 사건형(NONE) 규칙의 행은 활성 개념이 없고 발행 즉시 자동 완료라 자연히 제외된다.</p>
     *
     * <p>정렬(2026-10-06): <b>열린 행(cleared_at IS NULL)이 마지막</b>, 그 안에서는 마지막 성립 시각 오름차순. 호출부가 키별로
     * 마지막 put 을 남기므로 같은 키에 행이 여럿이어도 열린 행이 맵을 차지한다. 예전 {@code ORDER BY occurred_at} 은 재활성 행
     * (occurred_at 이 최초 시각)이나 timestamp 가 임의인 테스트 번들 때문에 해소된 행이 남고 열린 행이 고아가 됐다.</p>
     */
    public Flux<ActiveEventRow> loadActiveEvents() {
        String sql = ACTIVE_ROW_SELECT + """
                 WHERE e.acked_at IS NULL
                   AND COALESCE(r.suppress_mode, 'UNTIL_ACKED') <> 'NONE'
                 ORDER BY (e.cleared_at IS NULL) ASC, COALESCE(e.last_occurred_at, e.occurred_at) ASC
                """;
        return postgresDatabaseClient.sql(sql)
                .map(EventRuleReader::toActiveRow)
                .all()
                .doOnError(e -> log.error("[EventRuleReader] loadActiveEvents 실패: {}", e.getMessage()));
    }

    /**
     * 키 하나의 <b>열린</b> 행(cleared_at IS NULL). 확인 여부와 무관하게 찾는다 — 맵 재동기화({@code ActiveEventStore#resync})용.
     * 부분 유니크 {@code uk_event_open} 덕에 최대 1건이지만, point_id 가 NULL 인 키는 인덱스가 못 막으므로 최신 1건만 쓴다.
     */
    public Mono<ActiveEventRow> findOpenEvent(Long ruleId, Long deviceId, Long pointId) {
        String sql = ACTIVE_ROW_SELECT + """
                 WHERE e.rule_id = :ruleId
                   AND e.device_id = :deviceId
                   AND e.point_id IS NOT DISTINCT FROM :pointId
                   AND e.cleared_at IS NULL
                 ORDER BY COALESCE(e.last_occurred_at, e.occurred_at) DESC
                 LIMIT 1
                """;
        return postgresDatabaseClient.sql(sql)
                .bind("ruleId", ruleId)
                .bind("deviceId", deviceId)
                .bind("pointId", pointId != null ? Parameters.in(pointId) : Parameters.in(Long.class))
                .map(EventRuleReader::toActiveRow)
                .one()
                .doOnError(e -> log.error("[EventRuleReader] findOpenEvent 실패: {}", e.getMessage()));
    }

    private static ActiveEventRow toActiveRow(io.r2dbc.spi.Readable row) {
        return new ActiveEventRow(
                        row.get("event_id", UUID.class),
                        row.get("rule_id", Long.class),
                        row.get("device_id", Long.class),
                        row.get("point_id", Long.class),
                        row.get("level", String.class),
                        row.get("occurred_at", OffsetDateTime.class),
                        row.get("cleared_at", OffsetDateTime.class),
                        row.get("acked_at", OffsetDateTime.class),
                        row.get("rule_name", String.class),
                        row.get("event_code", String.class),
                        row.get("device_key", String.class),
                        row.get("tag_name", String.class),
                        row.get("point_display_name", String.class),
                        row.get("last_occurred_at", OffsetDateTime.class)
                );
    }

    // findAckedEventIds 는 2026-10-06 삭제 — 스윕이 확인된 행을 DB 에서 닫기도 해야 해서 EventWriter.closeAcked 로 옮김.

    private static int toInt(Integer v) {
        return v == null ? 0 : v;
    }

    /** jsonb outputs 문자열을 OutputSpec 으로 파싱. null/파싱실패 시 null(액션 없는 event). */
    private OutputSpec parseOutputs(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, OutputSpec.class);
        } catch (Exception e) {
            log.warn("[EventRuleReader] outputs 파싱 실패(null 처리): {}", e.getMessage());
            return null;
        }
    }
}
