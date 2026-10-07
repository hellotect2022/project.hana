package com.mpole.hana.event.pipeline.event.dto;

import com.mpole.hana.common.dto.EventClear;
import com.mpole.hana.common.dto.EventOccurrence;

/**
 * 평가 1건의 결과 — <b>발생</b> · <b>재활성</b> · <b>해소</b> 중 하나.
 *
 * <p>Active 축 도입 전에는 평가기가 "발생"만 뱉었기 때문에 클라이언트는 알람을 내릴 시점을
 * 알 수 없었다. 이제 조건이 풀리는 순간도 결과로 나온다.</p>
 *
 * @param stateless    사건형(suppress_mode=NONE) 발생. 활성맵에 등록된 적이 없으므로 INSERT 는
 *                     {@code cleared_at=occurred_at} 로 바로 닫혀 들어가고, 실패해도 슬롯을 풀 필요가 없다.
 * @param reactivation 해소·미확인 상태에서 조건이 다시 성립(2026-10-02 B안). {@code occurrence} 의 eventId 는
 *                     기존 이벤트와 같다. INSERT 가 아니라 UPDATE(cleared_at=null, occurrence_count+1) 이고
 *                     웹소켓으로 같은 id 의 발생 봉투를 다시 보낸다.
 */
public record EvalOutcome(EventOccurrence occurrence, EventClear clear, boolean stateless, boolean reactivation) {

    public static EvalOutcome occurred(EventOccurrence e) {
        return new EvalOutcome(e, null, false, false);
    }

    /** 사건형 발생 — 활성·해소 없이 1건으로 닫혀 기록된다. */
    public static EvalOutcome occurredStateless(EventOccurrence e) {
        return new EvalOutcome(e, null, true, false);
    }

    /** 해소·미확인 이벤트의 재활성 — 같은 eventId. */
    public static EvalOutcome reactivated(EventOccurrence e) {
        return new EvalOutcome(e, null, false, true);
    }

    public static EvalOutcome cleared(EventClear c) {
        return new EvalOutcome(null, c, false, false);
    }

    public boolean isOccurrence() {
        return occurrence != null;
    }
}
