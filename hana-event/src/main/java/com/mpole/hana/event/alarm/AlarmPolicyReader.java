package com.mpole.hana.event.alarm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.OutputSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 알람 정책/디바이스-카테고리/카테고리 트리를 R2DBC(DatabaseClient)로 조회한다.
 *
 * <p>SnapshotService 와 동일하게 스키마 한정 없이 unqualified 테이블명(core 스키마는
 * connection factory 의 schema 옵션으로 지정)으로 조회하며, 모든 반환은 Mono/Flux 이므로
 * 블로킹이 발생하지 않는다.</p>
 *
 * <p>digitaltwin 의 alarm enum 에 의존하지 않는다. operator/level/scope/condition_kind 등은
 * DB 에 enum name 문자열(예: "GTE", "CRITICAL", "CATEGORY", "THRESHOLD")로 저장되어 있으며
 * 여기서는 String 그대로 읽어 상위 계층(Cache/Evaluator)에 전달한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmPolicyReader {

    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;

    /**
     * 정책 원본 행(레벨 제외). display_type 컬럼은 SIMPLE/SOP enum name 문자열.
     */
    public record PolicyRow(
            Long policyId,
            String policyName,
            String scope,          // CATEGORY / DEVICE
            Long categoryId,       // scope=CATEGORY 일 때 사용(대분류/중분류/소분류 어느 것이든)
            Long deviceId,         // scope=DEVICE 일 때 사용
            String tagName,
            String pointType,      // AI / DI ...
            String conditionKind,  // THRESHOLD / TOGGLE
            String operator,       // GTE / LTE / GT / LT (nullable)
            String triggerValue,   // TOGGLE 트리거값(nullable)
            int sustainSec,
            int cooldownSec
    ) {
    }

    /**
     * 정책 레벨 행. threshold_value 는 THRESHOLD 조건의 등급별 임계값.
     */
    public record LevelRow(
            Long levelId,
            Long policyId,
            String level,          // CAUTION / WARNING / CRITICAL
            Double thresholdValue,
            String icon,
            String effect,
            int displayOrder,
            OutputSpec outputs     // 등급별 출력 채널 세트(jsonb 파싱, nullable)
    ) {
    }

    /**
     * 카테고리 트리 노드. depth: 대분류(0)/중분류(1)/소분류(2) 구분(설계 기준).
     */
    public record CategoryRow(
            Long categoryId,
            Long parentId,
            Integer depth
    ) {
    }

    /**
     * active=true 인 정책 목록을 조회한다.
     */
    public Flux<PolicyRow> loadActivePolicies() {
        String sql = """
                SELECT policy_id, policy_name, scope, category_id, device_id,
                       tag_name, point_type, condition_kind, operator, trigger_value,
                       sustain_sec, cooldown_sec
                FROM tbl_alarm_policy
                WHERE active = true
                """;
        return postgresDatabaseClient.sql(sql)
                .map((row, meta) -> new PolicyRow(
                        row.get("policy_id", Long.class),
                        row.get("policy_name", String.class),
                        row.get("scope", String.class),
                        row.get("category_id", Long.class),
                        row.get("device_id", Long.class),
                        row.get("tag_name", String.class),
                        row.get("point_type", String.class),
                        row.get("condition_kind", String.class),
                        row.get("operator", String.class),
                        row.get("trigger_value", String.class),
                        toInt(row.get("sustain_sec", Integer.class)),
                        toInt(row.get("cooldown_sec", Integer.class))
                ))
                .all()
                .doOnError(e -> log.error("[AlarmPolicyReader] loadActivePolicies 실패: {}", e.getMessage()));
    }

    /**
     * active=true 정책에 속한 레벨을 policyId 기준으로 그룹핑해 반환한다.
     * (2쿼리 방식: 정책 로드와 별개로 레벨을 한 번에 로드 후 in-memory group)
     */
    public Mono<Map<Long, List<LevelRow>>> loadLevelsByPolicyId() {
        String sql = """
                SELECT l.level_id, l.policy_id, l.level, l.threshold_value,
                       l.icon, l.effect, l.display_order, l.outputs
                FROM tbl_alarm_policy_level l
                JOIN tbl_alarm_policy p ON p.policy_id = l.policy_id
                WHERE p.active = true
                ORDER BY l.policy_id, l.display_order
                """;
        return postgresDatabaseClient.sql(sql)
                .map((row, meta) -> new LevelRow(
                        row.get("level_id", Long.class),
                        row.get("policy_id", Long.class),
                        row.get("level", String.class),
                        row.get("threshold_value", Double.class),
                        row.get("icon", String.class),
                        row.get("effect", String.class),
                        toInt(row.get("display_order", Integer.class)),
                        parseOutputs(row.get("outputs", String.class))
                ))
                .all()
                .collect(Collectors.groupingBy(LevelRow::policyId))
                .doOnError(e -> log.error("[AlarmPolicyReader] loadLevelsByPolicyId 실패: {}", e.getMessage()));
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
                .doOnError(e -> log.error("[AlarmPolicyReader] loadDeviceCategoryMap 실패: {}", e.getMessage()));
    }

    /**
     * deviceId → alarm_state(장비 알람 마스터 스위치) 맵. false 인 장비는 평가기에서 알람을 억제한다.
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
                .doOnError(e -> log.error("[AlarmPolicyReader] loadDeviceAlarmEnabled 실패: {}", e.getMessage()));
    }

    /**
     * 카테고리 트리(부모/깊이) 전체 노드. CATEGORY 정책을 하위 소분류로 전개할 때 사용.
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
                .doOnError(e -> log.error("[AlarmPolicyReader] loadCategoryTree 실패: {}", e.getMessage()));
    }

    private static int toInt(Integer v) {
        return v == null ? 0 : v;
    }

    /** jsonb outputs 문자열을 OutputSpec 으로 파싱. null/파싱실패 시 null(레거시 폴백). */
    private OutputSpec parseOutputs(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, OutputSpec.class);
        } catch (Exception e) {
            log.warn("[AlarmPolicyReader] outputs 파싱 실패(null 처리): {}", e.getMessage());
            return null;
        }
    }
}
