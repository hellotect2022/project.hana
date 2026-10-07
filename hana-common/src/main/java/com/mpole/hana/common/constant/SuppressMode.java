package com.mpole.hana.common.constant;

/**
 * 같은 규칙+포인트의 이벤트를 언제까지 억제할지({@code tbl_event_rule.suppress_mode}).
 *
 * <p>두 종류뿐이다(2026-10-02). {@link #UNTIL_ACKED} 는 상태형 — 활성 이벤트가 있으면 새로 발생시키지 않고
 * 관제자 확인(ack) 까지 억제한다. {@link #NONE} 은 Active 축 자체를 타지 않는 사건형이다.
 * 시간 기반 cooldown·sustain·"성립 중에만 억제(WHILE_ACTIVE)" 는 모두 폐기했다.</p>
 */
public enum SuppressMode {
    // WHILE_ACTIVE("성립 중 억제") 는 2026-10-02 삭제 — 기획 결정(시간·성립 개념 없이 확인(ack) 기준으로만 운영).
    // ⚠ DB 에 이 값이 남아 있으면 digitaltwin 의 EnumType.STRING 매핑이 깨진다 →
    //   scripts/event/2026-10-02-suppress-mode-until-acked.sql 을 digitaltwin 배포 **전** 에 실행한다.
    /**
     * 상태형(기본). 첫 발생 후 관제자가 확인(ack)하기 전까지 억제 — 활성 중 같은 조건은 무시하고,
     * 해소돼도 확인 전에는 새 이벤트를 만들지 않는다(확인 전 재성립은 같은 이벤트 재활성, 2026-10-02 B안).
     */
    UNTIL_ACKED("확인 전까지 억제"),
    /**
     * 억제 없음 — <b>사건형</b>(물리버튼·호출 등). 2026-10-01 추가.
     * <p>활성·해소 개념이 없다. 조건이 성립한 메시지 1건이 곧 이벤트 1건이며
     * {@code tbl_event} 에 {@code cleared_at=acked_at=occurred_at, acked_by=NULL}(자동 완료, 시스템) 로 바로 닫혀 기록된다.
     * 버튼처럼 값이 거짓으로 돌아오지 않는 신호를 WHILE_ACTIVE 로 두면 첫 번째 이후 영영 무시되므로 둔다.</p>
     */
    NONE("억제 없음(사건형)");

    private final String description;

    SuppressMode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
