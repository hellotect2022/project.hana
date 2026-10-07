package com.mpole.hana.event.pipeline.event;

import com.mpole.hana.event.dto.EventAck;
import com.mpole.hana.event.dto.EventHistoryDto;
import com.mpole.hana.event.pipeline.event.dto.ActiveEvent;
import com.mpole.hana.event.websocket.WebsocketBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 확인(ack) 처리 — 웹소켓 ACK 메시지의 기본 경로 (2026-10-02).
 *
 * <pre>
 *   client ─ws─▶ {"type":"ACK","eventId":…,"userId":…}
 *     ① tbl_event.acked_at/acked_by UPDATE, 미해소면 cleared_at 도 닫음  (정본. 이미 확인된 건은 0행 → 아무것도 안 함)
 *     ② ActiveEventStore.applyAck = 맵에서 제거                             (확인 = 종료. 다음 성립은 새 eventId)
 *     ③ 모든 세션에 {code}_ACK 브로드캐스트                                   (다른 화면도 같은 알람을 닫는다)
 * </pre>
 *
 * <p>평가 경로와 달리 DB 를 먼저 친다 — 확인은 드물고, "이미 확인됨" 판정의 정본이 DB 이기 때문이다.
 * 웹소켓을 거치지 않고 DB 에서 직접 확인된 건은 {@link ActiveEventStore} 의 30초 스윕이 보조로 따라잡는다(브로드캐스트 없음).
 * digitaltwin 의 REST {@code POST /ack} 는 2026-10-06 삭제 — 확인 경로는 이 웹소켓 하나다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAckService {

    private final EventWriter eventWriter;
    private final ActiveEventStore activeStore;
    private final WebsocketBroadcastService websocketBroadcastService;

    /** 확인 처리. 실패·무시는 로그만 남기고 스트림을 끊지 않는다. */
    public Mono<Void> ack(UUID eventId, Long userId) {
        OffsetDateTime now = OffsetDateTime.now();
        return eventWriter.ack(eventId, userId, now)
                .flatMap(code -> {
                    ActiveEvent entry = activeStore.applyAck(eventId);
                    log.info("[EventAck] 확인 처리 → 종료. eventId={}, userId={}, 활성맵={}\n{}",
                            eventId, userId, entry == null ? "없음(사건형/이미 종료)" : (entry.awaitingAck() ? "해소·미확인 제거" : "활성 중 제거"),
                            activeStore.dumpTable());
                    return websocketBroadcastService.publish(
                            EventHistoryDto.acked(new EventAck(eventId, code.orElse(null), now, userId)));
                })
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.debug("[EventAck] 이미 확인됐거나 없는 이벤트. eventId={}", eventId)))
                .onErrorResume(e -> {
                    log.error("[EventAck] 확인 처리 실패. eventId={}: {}", eventId, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}
