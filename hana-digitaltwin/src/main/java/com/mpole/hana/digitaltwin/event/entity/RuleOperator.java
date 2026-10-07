package com.mpole.hana.digitaltwin.event.entity;

import lombok.Getter;

/**
 * 이벤트 규칙 판정 연산자 — 규칙의 판정 방식은 이 값 하나로 정한다(2026-10-01, condition_kind 폐기).
 *
 * <ul>
 *   <li>GTE/LTE/GT/LT — 비교. 포인트 값(숫자)을 레벨별 {@code threshold_value} 와 비교한다. deadband 적용</li>
 *   <li>EQ — 일치. 포인트 값이 레벨별 {@code trigger_value} 집합(쉼표 구분, "2,3") 중 하나와 같으면 성립.
 *       숫자는 수치 동등(3 = 3.0), 문자열은 trim + 대소문자 무시. deadband 무관</li>
 * </ul>
 *
 * <p>DB 에는 상수명 문자열(GTE/LTE/GT/LT/EQ)로 저장된다. 설명값은 비교 심볼이라
 * 클라이언트가 조건 요약("&gt;= 12", "= 3") 을 만들 때 그대로 쓴다.</p>
 */
@Getter
public enum RuleOperator {
    GTE(">="),
    LTE("<="),
    GT(">"),
    LT("<"),
    EQ("=");

    private final String symbol;

    RuleOperator(String symbol) {
        this.symbol = symbol;
    }

    /** 일치(EQ) 연산자인지 — 레벨의 trigger_value 로 판정한다. */
    public boolean isEquality() {
        return this == EQ;
    }
}
