package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.digitaltwin.event.entity.EventDisplayType;
import com.mpole.hana.digitaltwin.event.entity.EventRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class EventRuleDtos {

    private EventRuleDtos() {
    }

    public record EventRuleRequest(
            @NotBlank String ruleName,
            String description,
            Long deviceId,
            String tagName,
            List<Map<String, Object>> conditions,
            String conditionLogic,
            Integer sustainDuration,
            Integer cooldown,
            String severity,
            List<String> actions,
            EventDisplayType displayType,
            Long sopTemplateId,
            Boolean active
    ) {
    }

    public record EventRuleResponse(
            Long ruleId,
            String ruleName,
            String description,
            Long deviceId,
            String tagName,
            List<Map<String, Object>> conditions,
            String conditionLogic,
            Integer sustainDuration,
            Integer cooldown,
            String severity,
            List<String> actions,
            EventDisplayType displayType,
            Long sopTemplateId,
            String sopTemplateName,
            Boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static EventRuleResponse from(EventRule rule) {
            return new EventRuleResponse(
                    rule.getRuleId(),
                    rule.getRuleName(),
                    rule.getDescription(),
                    rule.getDeviceId(),
                    rule.getTagName(),
                    rule.getConditions(),
                    rule.getConditionLogic(),
                    rule.getSustainDurationSec(),
                    rule.getCooldownSec(),
                    rule.getSeverity(),
                    rule.getActions(),
                    rule.getDisplayType(),
                    rule.getSopTemplate() != null ? rule.getSopTemplate().getTemplateId() : null,
                    rule.getSopTemplate() != null ? rule.getSopTemplate().getTemplateName() : null,
                    rule.getActive(),
                    rule.getCreatedAt(),
                    rule.getUpdatedAt()
            );
        }
    }

    public record EventInstanceCreateRequest(
            Long ruleId,
            @NotBlank String eventName,
            EventDisplayType displayType,
            Long deviceId,
            Long pointId,
            String locationText,
            String buildingCode,
            String floorCode,
            String zoneCode,
            Map<String, Object> context,
            String alarmTitle,
            String alarmDetail,
            String errorCode,
            String severity
    ) {
    }

    public record EventInstanceSummary(
            Long eventId,
            String eventName,
            EventDisplayType displayType,
            String locationText,
            OffsetDateTime occurredAt,
            String status,
            Long sopInstanceId,
            Integer currentStepNo
    ) {
    }
}
