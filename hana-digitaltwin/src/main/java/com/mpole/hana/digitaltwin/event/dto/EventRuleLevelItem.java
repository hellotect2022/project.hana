package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.digitaltwin.event.entity.EventEffect;
import com.mpole.hana.digitaltwin.event.entity.EventLevel;
import lombok.Builder;

@Builder
public record EventRuleLevelItem(
        EventLevel level,
        Double thresholdValue,
        String icon,
        EventEffect effect,
        int displayOrder,
        OutputSpec outputs      // 이 등급 발생 시 켤 출력 채널 세트(NOTIFY/SOP/CCTV/SMS)
) {
}
