package com.mpole.hana.digitaltwin.alarm.dto;

import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.digitaltwin.alarm.entity.AlarmLevel;
import lombok.Builder;

@Builder
public record AlarmPolicyLevelItem(
        AlarmLevel level,
        Double thresholdValue,
        String icon,
        String effect,
        int displayOrder,
        OutputSpec outputs      // 이 등급 발생 시 켤 출력 채널 세트(NOTIFY/SOP/CCTV/SMS)
) {
}
