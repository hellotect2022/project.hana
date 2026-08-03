package com.mpole.hana.digitaltwin.alarm.entity;

/**
 * 알람 조건 종류.
 * - THRESHOLD: 아날로그(AI) 임계치 비교 (operator + level별 thresholdValue)
 * - TOGGLE: 디지털(DI) 상태값 일치 비교 (triggerValue "0"/"1")
 */
public enum AlarmConditionKind {
    THRESHOLD("임계치(AI)"),
    TOGGLE("토글(DI)");

    private final String description;

    AlarmConditionKind(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
