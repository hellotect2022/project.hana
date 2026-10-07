package com.mpole.hana.digitaltwin.event.entity;

import lombok.Getter;

/**
 * 이벤트 파생 상태 — {@code cleared_at}(시스템)와 {@code acked_at}(사람) 두 축의 조합.
 *
 * <p>DB 컬럼이 아니라 계산값이다. 두 축을 따로 저장하는 이유는 서로 독립이기 때문이다 —
 * 값이 정상 복귀해도 아무도 확인하지 않았을 수 있고, 확인했어도 조건은 계속 성립할 수 있다.</p>
 *
 * <pre>
 *              acked_at = null        acked_at 있음
 *   cleared_at null   ACTIVE_UNACK           ACTIVE_ACK
 *   cleared_at 있음   CLEARED_UNACK          CLOSED
 * </pre>
 */
@Getter
public enum EventStatus {
    /** 조건 성립 중 · 미확인 — 관제자 조치가 필요한 상태. */
    ACTIVE_UNACK("발생·미확인"),
    /**
     * 조건 성립 중 · 확인됨.
     * <p>2026-10-02 부터 도달하지 않는다 — 확인 = 종료라서 확인 시 cleared_at 도 같이 닫힌다.
     * 그 이전에 쌓인 행을 위해 값만 남긴다.</p>
     */
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
