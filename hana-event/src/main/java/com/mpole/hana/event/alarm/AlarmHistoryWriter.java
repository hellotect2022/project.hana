package com.mpole.hana.event.alarm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.AlarmEvent;
import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

/**
 * 알람 이벤트 정본을 tbl_alarm_history 에 INSERT 하고 생성된 PK(alarm_id)를 돌려준다.
 *
 * <p>이 테이블 스키마는 digitaltwin(JPA ddl-auto)이 소유하지만 실제 쓰기는 event-server(R2DBC)가
 * 담당한다(설계: 이벤트 정본 일원화). alarm_id 가 곧 단일 eventId 로, 실시간 알림과 조립 API가
 * 이 id 로 이벤트를 가리킨다. outputs 는 발생 레벨의 출력 채널 세트 스냅샷(JSONB).</p>
 *
 * <p>JPA 를 우회하는 raw INSERT 이므로 created_at/updated_at 을 명시 세팅한다
 * (DateEntity 의 @PrePersist 가 동작하지 않음).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmHistoryWriter {

    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;

    private static final String INSERT_SQL = """
            INSERT INTO tbl_alarm_history
             (policy_id, policy_name, level, scope, device_id, device_key, point_id, tag_name,
              point_type, condition_kind, value_raw, threshold_value, operator, trigger_value,
              display_type, sop_template_id, outputs, icon, effect, effect_enabled,
              occurred_at, created_at, updated_at)
            VALUES
             (:policyId, :policyName, :level, :scope, :deviceId, :deviceKey, :pointId, :tagName,
              :pointType, :conditionKind, :valueRaw, :thresholdValue, :operator, :triggerValue,
              :displayType, :sopTemplateId, :outputs::jsonb, :icon, :effect, :effectEnabled,
              :occurredAt, :createdAt, :updatedAt)
            RETURNING alarm_id
            """;

    /**
     * 정본 INSERT 후 생성된 alarm_id 를 emit 한다. 실패 시 Mono.error.
     */
    public Mono<Long> insert(AlarmEvent ev) {
        String outputsJson = serializeOutputs(ev);
        OffsetDateTime now = OffsetDateTime.now();

        DatabaseClient.GenericExecuteSpec spec = postgresDatabaseClient.sql(INSERT_SQL)
                .bind("policyId", p(ev.policyId(), Long.class))
                .bind("policyName", p(ev.policyName(), String.class))
                .bind("level", p(ev.level(), String.class))
                .bind("scope", p(ev.scope(), String.class))
                .bind("deviceId", p(ev.deviceId(), Long.class))
                .bind("deviceKey", p(ev.deviceKey(), String.class))
                .bind("pointId", p(ev.pointId(), Long.class))
                .bind("tagName", p(ev.tagName(), String.class))
                .bind("pointType", p(ev.pointType(), String.class))
                .bind("conditionKind", p(ev.conditionKind(), String.class))
                .bind("valueRaw", p(ev.valueRaw(), String.class))
                .bind("thresholdValue", p(ev.thresholdValue(), Double.class))
                .bind("operator", p(ev.operator(), String.class))
                .bind("triggerValue", p(ev.triggerValue(), String.class))
                .bind("displayType", p(ev.displayType(), String.class))
                .bind("sopTemplateId", p(ev.sopTemplateId(), Long.class))
                .bind("outputs", p(outputsJson, String.class))
                .bind("icon", p(ev.icon(), String.class))
                .bind("effect", p(ev.effect(), String.class))
                .bind("effectEnabled", ev.effectEnabled())
                .bind("occurredAt", p(ev.occurredAt(), OffsetDateTime.class))
                .bind("createdAt", now)
                .bind("updatedAt", now);

        return spec.map((row, meta) -> row.get("alarm_id", Long.class))
                .one()
                .doOnError(e -> log.error("[AlarmHistoryWriter] alarm_history INSERT 실패: {}", e.getMessage()));
    }

    /** nullable 바인딩: 값이 있으면 그대로, null 이면 타입 지정 null 파라미터. */
    private static Parameter p(Object value, Class<?> type) {
        return value != null ? Parameters.in(value) : Parameters.in(type);
    }

    /** outputs 를 JSONB 문자열로 직렬화. null 이면 null(레거시 displayType 폴백). */
    private String serializeOutputs(AlarmEvent ev) {
        if (ev.outputs() == null) return null;
        try {
            return objectMapper.writeValueAsString(ev.outputs());
        } catch (Exception e) {
            log.warn("[AlarmHistoryWriter] outputs 직렬화 실패(null 저장): {}", e.getMessage());
            return null;
        }
    }
}
