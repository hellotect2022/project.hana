package com.mpole.hana.digitaltwin.event.entity;

import lombok.Getter;

/**
 * 이벤트 규칙 조건 종류.
 * - THRESHOLD: 아날로그(AI) 임계치 비교 (operator + level별 thresholdValue)
 * - TOGGLE: 디지털(DI) 상태값 일치 비교 (triggerValue "0"/"1")
 *
 * <p>DB 에는 상수명 문자열(THRESHOLD/TOGGLE)로 저장된다.</p>
 */
@Getter
public enum RuleConditionKind {
    THRESHOLD("임계치(AI)"),
    TOGGLE("토글(DI)");

    private final String description;

    RuleConditionKind(String description) {
        this.description = description;
    }

}
