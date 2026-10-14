package com.mpole.hana.digitaltwin.event.entity;

/**
 * 이벤트 심각도 등급. 선언 순서 = 오름차순 심각도(주의 → 경계 → 심각).
 *
 * <p>DB 에는 상수명 문자열(CAUTION/WARNING/CRITICAL)로 저장된다.</p>
 */
public enum EventLevel {
    CAUTION("lv1"),
    WARNING("lv2"),
    CRITICAL("lv3");

    private final String description;

    EventLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
