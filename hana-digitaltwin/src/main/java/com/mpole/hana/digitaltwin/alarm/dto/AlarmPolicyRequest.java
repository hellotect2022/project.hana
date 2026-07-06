package com.mpole.hana.digitaltwin.alarm.dto;

import com.mpole.hana.digitaltwin.alarm.entity.AlarmConditionKind;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmOperator;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmScope;
import com.mpole.hana.digitaltwin.event.entity.EventDisplayType;
import lombok.Builder;

import java.util.List;

@Builder
public record AlarmPolicyRequest(
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
        List<AlarmPolicyLevelItem> levels
) {
}
