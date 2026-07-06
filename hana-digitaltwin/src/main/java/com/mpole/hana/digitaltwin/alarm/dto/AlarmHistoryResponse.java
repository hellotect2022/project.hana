package com.mpole.hana.digitaltwin.alarm.dto;

import com.mpole.hana.digitaltwin.alarm.entity.AlarmHistory;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * 알람 발생 이력 단건 응답.
 */
@Builder
public record AlarmHistoryResponse(
        Long alarmId,
        Long policyId,
        String policyName,
        String level,
        String scope,
        Long deviceId,
        String deviceKey,
        Long pointId,
        String tagName,
        String pointType,
        String conditionKind,
        String valueRaw,
        Double thresholdValue,
        String operator,
        String triggerValue,
        String displayType,
        Long sopTemplateId,
        String icon,
        String effect,
        boolean effectEnabled,
        OffsetDateTime occurredAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AlarmHistoryResponse from(AlarmHistory h) {
        if (h == null) return null;
        return AlarmHistoryResponse.builder()
                .alarmId(h.getAlarmId())
                .policyId(h.getPolicyId())
                .policyName(h.getPolicyName())
                .level(h.getLevel())
                .scope(h.getScope())
                .deviceId(h.getDeviceId())
                .deviceKey(h.getDeviceKey())
                .pointId(h.getPointId())
                .tagName(h.getTagName())
                .pointType(h.getPointType())
                .conditionKind(h.getConditionKind())
                .valueRaw(h.getValueRaw())
                .thresholdValue(h.getThresholdValue())
                .operator(h.getOperator())
                .triggerValue(h.getTriggerValue())
                .displayType(h.getDisplayType())
                .sopTemplateId(h.getSopTemplateId())
                .icon(h.getIcon())
                .effect(h.getEffect())
                .effectEnabled(h.isEffectEnabled())
                .occurredAt(h.getOccurredAt())
                .createdAt(h.getCreatedAt())
                .updatedAt(h.getUpdatedAt())
                .build();
    }
}
