package com.mpole.hana.digitaltwin.event.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;

@Entity
@Table(name = "tbl_event_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRule extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "tag_name", length = 200)
    private String tagName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> conditions;

    @Column(name = "condition_logic", length = 8)
    @Builder.Default
    private String conditionLogic = "AND";

    @Column(name = "sustain_duration_sec")
    @Builder.Default
    private Integer sustainDurationSec = 0;

    @Column(name = "cooldown_sec")
    @Builder.Default
    private Integer cooldownSec = 0;

    @Column(length = 20)
    private String severity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> actions;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_type", length = 16)
    @Builder.Default
    private EventDisplayType displayType = EventDisplayType.SIMPLE;

    // SOP 템플릿 바인딩(soft 참조). 기존 SopTemplate 엔티티 결합 제거 — v2 SOP 재구축 시 재연결.
    @Column(name = "sop_template_id")
    private Long sopTemplateId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
