package com.mpole.hana.event.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 확인(ack) 통지 — 웹소켓 봉투 {@code {event_code}_ACK} 의 본문 (2026-10-02).
 *
 * <p>누가 어느 화면에서 확인했든 모든 접속 세션에 나간다. 클라이언트는 이 id 의 알람을 닫는다.</p>
 *
 * @param eventCode 발생 때와 같은 이벤트 코드(없으면 null → 봉투는 "EVENT_ACK")
 * @param ackedBy   확인자 user_id. 웹소켓 ACK 메시지에 userId 가 없었으면 null
 */
public record EventAck(
        UUID eventId,
        String eventCode,
        OffsetDateTime ackedAt,
        Long ackedBy
) {
}
