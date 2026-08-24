package com.mpole.hana.digitaltwin.alarm.entity;

/**
 * 알람 레벨별 3D 이펙트 연출 종류.
 */
public enum AlarmEffect {
    NONE("없음"),
    BLINK("깜박거림"),
    COLOR_CHANGE("색전환"),
    PULSE("펄스");

    private final String description;

    AlarmEffect(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
