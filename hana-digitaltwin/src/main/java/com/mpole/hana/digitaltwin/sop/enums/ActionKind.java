package com.mpole.hana.digitaltwin.sop.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 컴포넌트 부수효과 동작 (v2). 각 상수가 설명을 소유.
 * 흐름 제어(다음 STEP/종료)는 여기 없음 — STEP.next(전이 규칙)가 담당.
 */
public enum ActionKind {
    CALL("전화 다이얼(payload.number)"),
    BROADCAST("방송/텔레폰페이징 송출"),
    MARK_COMPLETE("완료 처리(토글)"),
    NONE("동작 없음(표시 전용)");

    private final String description;

    ActionKind(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 역직렬화 내성: 제거/미지원 값(옛 GOTO_STEP·CLOSE 등)은 NONE 으로 읽는다.
     * → 스키마 축소 후에도 기존 jsonb 로드가 crash 나지 않음.
     */
    @JsonCreator
    public static ActionKind from(String value) {
        if (value == null) {
            return null;
        }
        for (ActionKind a : values()) {
            if (a.name().equals(value)) {
                return a;
            }
        }
        return NONE;
    }
}
