package com.mpole.hana.digitaltwin.alarm.dto;

import com.mpole.hana.digitaltwin.alarm.entity.AlarmConditionKind;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmOperator;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmPolicy;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmScope;
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
        String categoryPath,
        Long deviceId,
        String deviceName,
        String tagName,
        String pointType,
        AlarmConditionKind conditionKind,
        AlarmOperator operator,
        String triggerValue,
        int sustainSec,
        int cooldownSec,
        Boolean active,
        List<AlarmPolicyLevelItem> levels,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AlarmPolicyResponse from(AlarmPolicy p) {
        return from(p, null, null);
    }

    /** deviceName/categoryPath 는 소프트 참조(deviceId/categoryId)를 서비스에서 resolve 해 전달한다. */
    public static AlarmPolicyResponse from(AlarmPolicy p, String deviceName, String categoryPath) {
        if (p == null) return null;

        List<AlarmPolicyLevelItem> levelItems = p.getLevels().stream()
                .map(l -> AlarmPolicyLevelItem.builder()
                        .level(l.getLevel())
                        .thresholdValue(l.getThresholdValue())
                        .icon(l.getIcon())
                        .effect(l.getEffect())
                        .displayOrder(l.getDisplayOrder())
                        .outputs(l.getOutputs())
                        .build())
                .toList();

        return AlarmPolicyResponse.builder()
                .policyId(p.getPolicyId())
                .policyName(p.getPolicyName())
                .description(p.getDescription())
                .scope(p.getScope())
                .categoryId(p.getCategoryId())
                .categoryPath(categoryPath)
                .deviceId(p.getDeviceId())
                .deviceName(deviceName)
                .tagName(p.getTagName())
                .pointType(p.getPointType())
                .conditionKind(p.getConditionKind())
                .operator(p.getOperator())
                .triggerValue(p.getTriggerValue())
                .sustainSec(p.getSustainSec())
                .cooldownSec(p.getCooldownSec())
                .active(p.getActive())
                .levels(levelItems)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
