package com.mpole.hana.digitaltwin.alarm.entity;

import com.mpole.hana.digitaltwin.event.entity.EventDisplayType;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 알람 정책.
 * 특정 카테고리 또는 장비의 태그(포인트)에 대해 임계치/토글 조건을 정의하고,
 * 심각도 등급(levels)별 표현/이펙트를 관리한다.
 * category/device/sopTemplate 는 소프트 참조(FK 없이 id 만 보관)한다.
 */
@Entity
@Table(name = "tbl_alarm_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlarmPolicy extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmScope scope;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "tag_name")
    private String tagName;

    @Column(name = "point_type")
    private String pointType;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_kind")
    private AlarmConditionKind conditionKind;

    @Enumerated(EnumType.STRING)
    private AlarmOperator operator;

    @Column(name = "trigger_value")
    private String triggerValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_type")
    @Builder.Default
    private EventDisplayType displayType = EventDisplayType.SIMPLE;

    @Column(name = "sop_template_id")
    private Long sopTemplateId;

    @Column(name = "effect_enabled")
    @Builder.Default
    private boolean effectEnabled = false;

    @Column(name = "sustain_sec")
    @Builder.Default
    private int sustainSec = 0;

    @Column(name = "cooldown_sec")
    @Builder.Default
    private int cooldownSec = 0;

    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AlarmPolicyLevel> levels = new ArrayList<>();

    public void addLevel(AlarmPolicyLevel level) {
        level.setPolicy(this);
        this.levels.add(level);
    }

    public void clearLevels() {
        this.levels.clear();
    }
}
