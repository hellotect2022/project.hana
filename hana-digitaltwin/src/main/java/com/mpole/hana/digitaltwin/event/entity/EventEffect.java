package com.mpole.hana.digitaltwin.event.entity;

/**
 * 이벤트 레벨별 3D 이펙트 연출 종류.
 *
 * <p>DB 에는 상수명 문자열(NONE/BLINK/COLOR_CHANGE/PULSE)로 저장된다.</p>
 */
public enum EventEffect {
    NONE("없음"),
    BLINK("점멸"),
    COLOR_CHANGE("색전환"),
    PULSE("펄스");

    private final String description;

    EventEffect(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
