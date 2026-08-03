package com.mpole.hana.event.alarm;

import com.mpole.hana.common.dto.OutputSpec;

import java.util.List;

/**
 * 평가에 필요한 정책의 불변 뷰. Reader 의 PolicyRow + LevelRow 를 결합한 조회 최적화 형태.
 *
 * <p>모듈 간 kafka 계약(AlarmEvent)과 동일하게 digitaltwin enum 에 의존하지 않고
 * String 으로 값을 보관한다(operator/level/scope/conditionKind).</p>
 *
 * @param levels displayOrder 순으로 정렬된 등급 목록(THRESHOLD 는 임계값 비교, TOGGLE 은 대표 등급 선택)
 */
public record PolicyView(
        Long policyId,
        String policyName,
        String scope,          // CATEGORY / DEVICE
        String tagName,
        String pointType,      // AI / DI ...
        String conditionKind,  // THRESHOLD / TOGGLE
        String operator,       // GTE / LTE / GT / LT (nullable)
        String triggerValue,   // TOGGLE 트리거값(nullable)
        String displayType,    // SIMPLE / SOP
        Long sopTemplateId,
        boolean effectEnabled,
        int sustainSec,
        int cooldownSec,
        List<PolicyLevelView> levels
) {

    /**
     * 정책 등급 불변 뷰.
     */
    public record PolicyLevelView(
            String level,          // CAUTION / WARNING / CRITICAL
            Double thresholdValue,
            String icon,
            String effect,
            int displayOrder,
            OutputSpec outputs     // 이 등급 발생 시 출력 채널 세트(nullable)
    ) {
    }
}
