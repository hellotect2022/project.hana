package com.mpole.hana.digitaltwin.event.entity;

import lombok.Getter;

/**
 * THRESHOLD 조건 비교 연산자.
 *
 * <p>DB 에는 상수명 문자열(GTE/LTE/GT/LT)로 저장된다. 설명값은 비교 심볼이라
 * 클라이언트가 조건 요약("&gt;= 12")을 만들 때 그대로 쓴다.</p>
 */
@Getter
public enum RuleOperator {
    GTE(">="),
    LTE("<="),
    GT(">"),
    LT("<");

    private final String symbol;

    RuleOperator(String symbol) {
        this.symbol = symbol;
    }

}
