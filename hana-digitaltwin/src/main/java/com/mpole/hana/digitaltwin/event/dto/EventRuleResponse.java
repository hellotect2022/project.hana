package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.common.constant.SuppressMode;
import com.mpole.hana.digitaltwin.event.entity.RuleConditionKind;
import com.mpole.hana.digitaltwin.event.entity.RuleOperator;
import com.mpole.hana.digitaltwin.event.entity.EventRule;
import com.mpole.hana.digitaltwin.event.entity.RuleScope;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record EventRuleResponse(
        Long ruleId,
        String ruleName,
        String description,
        RuleScope scope,
        Long categoryId,
        String categoryPath,
        Long deviceId,
        String deviceName,
        String tagName,
        String pointType,
        RuleConditionKind conditionKind,
        RuleOperator operator,
        String triggerValue,
        int sustainSec,
        int cooldownSec,
        Double deadband,
        SuppressMode suppressMode,
        Boolean active,
        List<EventRuleLevelItem> levels,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static EventRuleResponse from(EventRule p) {
        return from(p, null, null);
    }

    /** deviceName/categoryPath 는 소프트 참조(deviceId/categoryId)를 서비스에서 resolve 해 전달한다. */
    public static EventRuleResponse from(EventRule p, String deviceName, String categoryPath) {
        if (p == null) return null;

        List<EventRuleLevelItem> levelItems = p.getLevels().stream()
                .map(l -> EventRuleLevelItem.builder()
                        .level(l.getLevel())
                        .thresholdValue(l.getThresholdValue())
                        .icon(l.getIcon())
                        .effect(l.getEffect())
                        .displayOrder(l.getDisplayOrder())
                        .outputs(l.getOutputs())
                        .build())
                .toList();

        return EventRuleResponse.builder()
                .ruleId(p.getRuleId())
                .ruleName(p.getRuleName())
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
                .deadband(p.getDeadband())
                .suppressMode(p.getSuppressMode())
                .active(p.getActive())
                .levels(levelItems)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
