package com.mpole.hana.digitaltwin.alarm.dto;

import com.mpole.hana.digitaltwin.alarm.entity.AlarmLevel;
import lombok.Builder;

@Builder
public record AlarmPolicyLevelItem(
        AlarmLevel level,
        Double thresholdValue,
        String icon,
        String effect,
        int displayOrder
) {
}
