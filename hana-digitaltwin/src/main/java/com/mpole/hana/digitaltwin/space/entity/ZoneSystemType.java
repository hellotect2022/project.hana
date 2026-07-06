package com.mpole.hana.digitaltwin.space.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 표현정책 시스템 구분 — WA-SPACE-LIGHT 의 탭(조명/화재감지).
 * 같은 Zone(위치)이라도 시스템별로 별도 표현정책을 가진다.
 */
@Getter
@RequiredArgsConstructor
public enum ZoneSystemType {
    LIGHTING("조명 구역"),
    FIRE_DETECTION("화재감지 구역");

    private final String label;

    public static ZoneSystemType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("systemType 은 필수입니다.");
        }
        for (ZoneSystemType t : values()) {
            if (t.name().equalsIgnoreCase(code.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 systemType: " + code);
    }
}
