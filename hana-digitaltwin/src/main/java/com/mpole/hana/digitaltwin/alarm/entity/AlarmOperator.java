package com.mpole.hana.digitaltwin.alarm.entity;

/**
 * THRESHOLD 조건 비교 연산자.
 */
public enum AlarmOperator {
    GTE(">="),
    LTE("<="),
    GT(">"),
    LT("<");

    private final String symbol;

    AlarmOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
