package com.mpole.hana.digitaltwin.sop.enums;

/**
 * SOP 실행 인스턴스 상태.
 */
public enum SopInstanceStatus {
    PENDING,      // 생성됨(대기)
    IN_PROGRESS,  // 진행 중
    COMPLETED,    // 정상 완료
    TIMEOUT,      // 시간 초과
    CANCELLED     // 취소/종료
}
