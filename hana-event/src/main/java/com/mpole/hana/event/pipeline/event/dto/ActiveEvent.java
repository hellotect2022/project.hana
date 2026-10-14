package com.mpole.hana.event.pipeline.event.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 인메모리 활성 이벤트 1건 — 중복 억제 판정의 단위 (상태형 UNTIL_ACKED 전용. 사건형 NONE 은 이 맵을 타지 않는다).
 *
 * <p>맵에 있다 = 미확인. 확인(ack)은 조건 성립 여부와 무관하게 <b>종료</b>라서 항목이 제거된다(2026-10-02 결정).
 * 그래서 플래그는 하나뿐이다:</p>
 * <pre>
 *   awaitingAck=false   ① 활성·미확인 — 조건 성립 중. 같은 조건 무시
 *   awaitingAck=true    ② 해소·미확인 — 값은 정상인데 확인 전. 재성립하면 같은 eventId 재활성
 *   (제거)              종료 — 확인됨. 다음 성립은 새 eventId
 * </pre>
 *
 * <p>판정에 쓰는 건 {@code eventId/ruleId/deviceId/pointId/level/awaitingAck} 뿐이다. 나머지(규칙명·장비키·태그·포인트명·시각)는
 * 개발자가 로그 격자({@code ActiveEventStore#dumpTable})에서 한눈에 보기 위한 표시용이다(2026-10-06).</p>
 *
 * @param awaitingAck 해소됐지만 미확인. 조건 판정에서는 "떠 있지 않음" 으로 보되, 재성립은 새 이벤트가 아니라 재활성이다
 * @param occurredAt  최초 발생 시각(정본 occurred_at)
 * @param updatedAt   이 항목의 마지막 전이 시각(발생·해소·재활성·복원)
 */
public record ActiveEvent(
        UUID eventId,
        Long ruleId,
        Long deviceId,
        Long pointId,
        String level,
        boolean awaitingAck,
        String ruleName,
        String eventCode,
        String deviceKey,
        String tagName,
        String pointName,
        OffsetDateTime occurredAt,
        OffsetDateTime updatedAt
) {

    /** 활성 맵 키. point_id 가 NULL 이어도 하나의 키로 묶인다(DB 유니크 인덱스가 못 막는 구멍을 메움). */
    public static String key(Long ruleId, Long deviceId, Long pointId) {
        return ruleId + "|" + deviceId + "|" + pointId;
    }

    public String key() {
        return key(ruleId, deviceId, pointId);
    }

    /** 사람이 읽는 상태 라벨. */
    public String stateLabel() {
        return awaitingAck ? "해소·미확인" : "활성";
    }

    /** 해소 → 확인 대기(②). */
    public ActiveEvent withAwaitingAck() {
        return new ActiveEvent(eventId, ruleId, deviceId, pointId, level, true,
                ruleName, eventCode, deviceKey, tagName, pointName, occurredAt, OffsetDateTime.now());
    }

    /** 확인 대기 중 재성립 → 다시 활성(①). 같은 eventId 를 유지한다. */
    public ActiveEvent reactivated() {
        return new ActiveEvent(eventId, ruleId, deviceId, pointId, level, false,
                ruleName, eventCode, deviceKey, tagName, pointName, occurredAt, OffsetDateTime.now());
    }
}
