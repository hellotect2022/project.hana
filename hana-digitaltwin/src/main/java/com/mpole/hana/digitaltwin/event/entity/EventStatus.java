package com.mpole.hana.digitaltwin.event.entity;

import lombok.Getter;

/**
 * 이벤트 파생 상태 — {@code active}(시스템)와 {@code acked_at}(사람) 두 축의 조합.
 *
 * <p>DB 컬럼이 아니라 계산값이다. 두 축을 따로 저장하는 이유는 서로 독립이기 때문이다 —
 * 값이 정상 복귀해도 아무도 확인하지 않았을 수 있고, 확인했어도 조건은 계속 성립할 수 있다.</p>
 *
 * <pre>
 *              acked_at = null        acked_at 있음
 *   active     ACTIVE_UNACK           ACTIVE_ACK
 *   !active    CLEARED_UNACK          CLOSED
 * </pre>
 */
@Getter
public enum EventStatus {
    /** 조건 성립 중 · 미확인 — 운전원 조치가 필요한 상태. */
    ACTIVE_UNACK("발생·미확인"),
    /** 조건 성립 중 · 확인됨 — 인지했으나 아직 해소되지 않음. */
    ACTIVE_ACK("발생·확인됨"),
    /** 정상 복귀 · 미확인 — 저절로 해소됐으나 아무도 못 봄. */
    CLEARED_UNACK("해소·미확인"),
    /** 정상 복귀 · 확인됨 — 완전 종료. */
    CLOSED("종료");

    private final String description;

    EventStatus(String description) {
        this.description = description;
    }

}
