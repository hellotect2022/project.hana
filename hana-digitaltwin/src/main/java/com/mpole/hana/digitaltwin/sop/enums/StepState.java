package com.mpole.hana.digitaltwin.sop.enums;

/**
 * STEP 생애주기 상태 (v2). 각 상수가 설명을 소유.
 */
public enum StepState {
    LOCKED("비활성(아직 진입 전)"),
    PENDING("활성·진행 중(미완료)"),
    SUCCESS("완료"),
    SKIPPED("GOTO_STEP으로 지나쳐 건너뜀");

    private final String description;

    StepState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
