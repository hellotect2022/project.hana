package com.mpole.hana.digitaltwin.buildingLighting.domain;

/**
 * 개별 조명 장비의 점/소등 상태.
 * <p>실시간 포인트(tbl_device_current_value.points)의 ON/OFF tag 값(valueRaw)을 해석한 결과.
 * 해당 포인트가 없거나 값이 모호하면 {@link #UNKNOWN}.
 */
public enum LightingState {
    ON,
    OFF,
    UNKNOWN;

    /** DI 포인트의 valueRaw(예: "1"/"0", "on"/"off")를 상태로 변환 */
    public static LightingState fromRaw(String valueRaw) {
        if (valueRaw == null || valueRaw.isBlank()) {
            return UNKNOWN;
        }
        return switch (valueRaw.trim().toLowerCase()) {
            case "1", "on", "true", "y" -> ON;
            case "0", "off", "false", "n" -> OFF;
            default -> UNKNOWN;
        };
    }

    public boolean isOn() {
        return this == ON;
    }

    public boolean isOff() {
        return this == OFF;
    }
}
