package com.mpole.hana.digitaltwin.unityAsset.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 장비 에셋 타입.
 * DB에는 enum name(영문)으로 저장(@Enumerated(EnumType.STRING))되고,
 * 클라이언트에는 label(한글)을 함께 내려 표시한다.
 */
@Getter
@RequiredArgsConstructor
public enum AssetType {
    EQUIPMENT("장비"),
    EFFECT("이펙트"),
    ZONE("구역"),
    EVACUATION_ROUTE("피난대피로"),
    TOILET_STALL("화장실 칸"),
    PARKING_SLOT("주차 면"),
    PIPE("배관");

    private final String label;

    /** 영문 코드(name) 또는 null/blank 입력을 안전하게 enum으로 변환. 기본값 EQUIPMENT. */
    public static AssetType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return EQUIPMENT;
        }
        for (AssetType type : values()) {
            if (type.name().equalsIgnoreCase(code.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 에셋 타입입니다: " + code);
    }
}
