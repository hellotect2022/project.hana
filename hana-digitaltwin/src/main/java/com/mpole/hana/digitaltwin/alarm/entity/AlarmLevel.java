package com.mpole.hana.digitaltwin.alarm.entity;

/**
 * 알람 심각도 등급. 선언 순서 = 오름차순 심각도(주의 → 경계 → 심각).
 */
public enum AlarmLevel {
    CAUTION("주의"),
    WARNING("경계"),
    CRITICAL("심각");

    private final String description;

    AlarmLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
