package com.mpole.hana.event.pipeline.event.dto;

import com.mpole.hana.common.dto.OutputSpec;

/**
 * {@code tbl_event_rule_level} 원본 행.
 * <ul>
 *   <li>{@code thresholdValue} — 비교 연산자(GTE/LTE/GT/LT) 규칙의 등급별 임계값</li>
 *   <li>{@code triggerValue} — 일치 연산자(EQ) 규칙의 등급별 매칭값. 쉼표로 여러 값("2,3")</li>
 * </ul>
 */
public record RuleLevelRow(
        Long levelId,
        Long ruleId,
        String level,          // CAUTION / WARNING / CRITICAL
        Double thresholdValue,
        String triggerValue,
        String icon,
        String effect,
        int displayOrder,
        OutputSpec outputs     // 등급별 출력 채널 세트(jsonb 파싱, nullable)
) {
}
