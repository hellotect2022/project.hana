package com.mpole.hana.common.constant;

/**
 * 이벤트 연동 디바이스 제어의 실행 시점 (2026-10-12).
 *
 * <p>규칙 레벨의 {@code outputs.deviceControls[].trigger} 로 제어 항목마다 지정한다. 지정이 없으면 {@link #ON_OCCUR}.</p>
 * <pre>
 *   ON_OCCUR  이벤트 발생 직후 (예: 화재 → 배연창 열기)
 *   ON_ACK    관제자 확인(ack)이 끝난 직후 (예: 확인 → 배연창 닫기·경보 해제)
 * </pre>
 * <p>둘 다 이벤트당 한 번만 실행된다 — 발생은 INSERT 성공 때, 확인은 최초 확인 때. 재활성·30초 스윕 확인으로는 실행하지 않는다.
 * 사건형(NONE)은 발생과 동시에 자동 완료되므로 두 시점이 발생 순간에 연달아 실행된다.</p>
 */
public enum ControlTrigger {
    ON_OCCUR("발생 시"),
    ON_ACK("확인 후");

    private final String label;

    ControlTrigger(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
