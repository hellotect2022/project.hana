package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.common.constant.SuppressMode;
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
        String eventCode,
        String description,
        RuleScope scope,
        Long categoryId,
        String categoryPath,
        Long deviceId,
        String deviceName,
        String tagName,
        String pointType,
        RuleOperator operator,
        int sustainSec,
        Double deadband,
        SuppressMode suppressMode,
        Boolean active,
        boolean simulated,       // 모의 시나리오 전용(SOP 훈련) 규칙 여부. 2026-10-07
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
                        .triggerValue(l.getTriggerValue())
                        .icon(l.getIcon())
                        .effect(l.getEffect())
                        .displayOrder(l.getDisplayOrder())
                        .outputs(l.getOutputs())
                        .build())
                .toList();

        return EventRuleResponse.builder()
                .ruleId(p.getRuleId())
                .ruleName(p.getRuleName())
                .eventCode(p.getEventCode())
                .description(p.getDescription())
                .scope(p.getScope())
                .categoryId(p.getCategoryId())
                .categoryPath(categoryPath)
                .deviceId(p.getDeviceId())
                .deviceName(deviceName)
                .tagName(p.getTagName())
                .pointType(p.getPointType())
                .operator(p.getOperator())
                .sustainSec(p.getSustainSec())
                .deadband(p.getDeadband())
                .suppressMode(p.getSuppressMode())
                .active(p.getActive())
                .simulated(p.isSimulated())
                .levels(levelItems)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
