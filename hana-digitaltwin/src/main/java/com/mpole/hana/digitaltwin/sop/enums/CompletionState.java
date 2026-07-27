package com.mpole.hana.digitaltwin.sop.enums;

/**
 * 개별 완료 토글 상태 (COMPLETE_ACTION 등 컴포넌트 스코프). 각 상수가 설명을 소유.
 * STEP 생애주기(StepState)와 구분된다.
 */
public enum CompletionState {
    PENDING("미완료"),
    DONE("완료");

    private final String description;

    CompletionState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
