package com.mpole.hana.digitaltwin.alarm.entity;

import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 알람 발생 이력.
 *
 * <p>이 테이블(tbl_alarm_history)의 스키마는 digitaltwin(JPA, ddl-auto=update)이 소유·생성하며,
 * 실제 INSERT(쓰기)는 event-server(WebFlux/R2DBC)가 담당한다. digitaltwin 은 읽기 전용
 * 조회 API 만 제공한다.</p>
 *
 * <p>event-server 가 문자열로 write 하는 값(level/scope/operator/conditionKind/pointType 등)은
 * enum 매핑을 강제하지 않고 String 으로 저장한다. 정책/장비/포인트/SOP 는 소프트 참조(FK 없이 id 보관).</p>
 */
@Entity
@Table(
        name = "tbl_alarm_history",
        indexes = {
                @Index(name = "idx_alarm_history_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_alarm_history_level", columnList = "level"),
                @Index(name = "idx_alarm_history_device_id", columnList = "device_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlarmHistory extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alarm_id")
    private Long alarmId;

    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "policy_name")
    private String policyName;

    /** AlarmLevel name (CAUTION/WARNING/CRITICAL) — 문자열 저장 */
    @Column(name = "level")
    private String level;

    /** AlarmScope name (CATEGORY/DEVICE 등) — 문자열 저장 */
    @Column(name = "scope")
    private String scope;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "device_key")
    private String deviceKey;

    @Column(name = "point_id")
    private Long pointId;

    @Column(name = "tag_name")
    private String tagName;

    @Column(name = "point_type")
    private String pointType;

    @Column(name = "condition_kind")
    private String conditionKind;

    @Column(name = "value_raw")
    private String valueRaw;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "operator")
    private String operator;

    @Column(name = "trigger_value")
    private String triggerValue;

    @Column(name = "display_type")
    private String displayType;

    @Column(name = "sop_template_id")
    private Long sopTemplateId;

    /** 발생 레벨의 출력 채널 세트 스냅샷(NOTIFY/SOP/CCTV/SMS). 조립 API가 이 값으로 SOP/CCTV 표출 결정. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outputs", columnDefinition = "jsonb")
    private OutputSpec outputs;

    @Column(name = "icon")
    private String icon;

    @Column(name = "effect")
    private String effect;

    @Column(name = "effect_enabled")
    private boolean effectEnabled;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;
}
