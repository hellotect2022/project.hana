package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.digitaltwin.event.entity.EventEffect;
import com.mpole.hana.digitaltwin.event.entity.EventLevel;
import lombok.Builder;

/**
 * 규칙의 등급 1건(요청/응답 공용).
 *
 * <p>{@code thresholdValue} 는 비교 연산자(GTE/LTE/GT/LT) 규칙에서, {@code triggerValue} 는 일치 연산자(EQ)
 * 규칙에서 쓴다. 둘은 배타적이며 규칙의 operator 가 어느 쪽을 읽을지 정한다.</p>
 */
@Builder
public record EventRuleLevelItem(
        EventLevel level,
        Double thresholdValue,
        String triggerValue,    // EQ 용 매칭값 집합, 쉼표 구분("2,3")
        String icon,
        EventEffect effect,
        int displayOrder,
        OutputSpec outputs      // 이 등급 발생 시 켤 출력 채널 세트(NOTIFY/SOP/CCTV/SMS)
) {
}
