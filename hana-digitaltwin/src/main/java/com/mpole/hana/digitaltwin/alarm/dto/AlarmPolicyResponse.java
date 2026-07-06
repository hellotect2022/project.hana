package com.mpole.hana.digitaltwin.alarm.dto;

import com.mpole.hana.digitaltwin.alarm.entity.AlarmConditionKind;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmOperator;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmPolicy;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmScope;
import com.mpole.hana.digitaltwin.event.entity.EventDisplayType;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record AlarmPolicyResponse(
        Long policyId,
        String policyName,
        String description,
        AlarmScope scope,
        Long categoryId,
        Long deviceId,
        String tagName,
        String pointType,
        AlarmConditionKind conditionKind,
        AlarmOperator operator,
        String triggerValue,
        EventDisplayType displayType,
        Long sopTemplateId,
        boolean effectEnabled,
        int sustainSec,
        int cooldownSec,
        Boolean active,
        List<AlarmPolicyLevelItem> levels,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AlarmPolicyResponse from(AlarmPolicy p) {
        if (p == null) return null;

        List<AlarmPolicyLevelItem> levelItems = p.getLevels().stream()
                .map(l -> AlarmPolicyLevelItem.builder()
                        .level(l.getLevel())
                        .thresholdValue(l.getThresholdValue())
                        .icon(l.getIcon())
                        .effect(l.getEffect())
                        .displayOrder(l.getDisplayOrder())
                        .build())
                .toList();

        return AlarmPolicyResponse.builder()
                .policyId(p.getPolicyId())
                .policyName(p.getPolicyName())
                .description(p.getDescription())
                .scope(p.getScope())
                .categoryId(p.getCategoryId())
                .deviceId(p.getDeviceId())
                .tagName(p.getTagName())
                .pointType(p.getPointType())
                .conditionKind(p.getConditionKind())
                .operator(p.getOperator())
                .triggerValue(p.getTriggerValue())
                .displayType(p.getDisplayType())
                .sopTemplateId(p.getSopTemplateId())
                .effectEnabled(p.isEffectEnabled())
                .sustainSec(p.getSustainSec())
                .cooldownSec(p.getCooldownSec())
                .active(p.getActive())
                .levels(levelItems)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
