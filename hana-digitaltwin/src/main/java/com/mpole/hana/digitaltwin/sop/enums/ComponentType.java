package com.mpole.hana.digitaltwin.sop.enums;

/**
 * SOP 컴포넌트 종류 (v2). 각 상수가 설명을 소유 — /api/sop/meta 에서 {값:설명}으로 노출.
 * STEP 은 자식 components 를 품는 컨테이너 컴포넌트.
 */
public enum ComponentType {
    STEP("컨테이너(헤더 + 자식 components)"),
    LABEL("지시문/설명 텍스트"),
    COMPLETE_ACTION("작업행 + 완료 토글 버튼"),
    BUTTON_GROUP("선택 버튼 묶음(분기·액션)"),
    PHONE_CONTACT("기관명 + 전화번호 + 다이얼"),
    NOTE("안내/주석 문구"),
    GROUP("논리 그룹 컨테이너(테두리 없음) — 자식 components 를 시각적으로 묶음"),
    FRAME("테두리 박스 컨테이너(시각) — 자식 components 를 테두리로 감쌈");

    private final String description;

    ComponentType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
