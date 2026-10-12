package com.mpole.hana.event.pipeline.event;

import com.mpole.hana.common.constant.ControlTrigger;
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
    private final EventControlClient controlClient;

    /** 확인 결과. 거절({@link #REJECTED_SOP_RESULT_REQUIRED})이면 호출부(웹소켓 핸들러)가 보낸 세션에만 알린다. */
    public enum AckResult {
        ACKED,
        /** 이미 확인된 이벤트 — 조용히 무시(최초 확인자·시각 보존) */
        ALREADY_ACKED,
        /** SOP 이벤트인데 sopResult 가 없음 — 확인하지 않음 */
        REJECTED_SOP_RESULT_REQUIRED,
        NOT_FOUND,
        FAILED
    }

    public static final String REASON_SOP_RESULT_REQUIRED =
            "SOP 이벤트는 확인 시 sopResult(SOP 클릭 기록)가 필요합니다. SOP 를 끝까지 완료한 뒤 다시 확인하세요.";

    /**
     * 확인 처리. 실패·무시는 로그만 남기고 스트림을 끊지 않는다.
     *
     * @param sopResultJson SOP 클릭 기록 스냅샷(JSON 문자열, 형식 자유, 2026-10-12). SOP 이벤트면 필수 — 없으면 거절.
     *                      SOP 가 아닌 이벤트면 무시하고 확인만 한다. 판별은 DB(sop_enabled) 로 한다(활성맵 아님).
     */
    public Mono<AckResult> ack(UUID eventId, Long userId, String sopResultJson) {
        OffsetDateTime now = OffsetDateTime.now();
        return eventWriter.ack(eventId, userId, now, sopResultJson)
                .flatMap(code -> {
                    ActiveEvent entry = activeStore.applyAck(eventId);
                    log.info("[EventAck] 확인 처리 → 종료. eventId={}, userId={}, 활성맵={}\n{}",
                            eventId, userId, entry == null ? "없음(사건형/이미 종료)" : (entry.awaitingAck() ? "해소·미확인 제거" : "활성 중 제거"),
                            activeStore.dumpTable());
                    // 확인 후 디바이스 제어(2026-10-12) — 최초 확인 때만 여기 온다(이미 확인된 건은 UPDATE 0행 → switchIfEmpty).
                    // 이 이벤트에 ON_ACK 제어가 있는지는 digitaltwin 이 outputs 를 보고 판단한다(없으면 아무것도 안 함).
                    controlClient.fire(eventId, ControlTrigger.ON_ACK);
                    return websocketBroadcastService.publish(
                                    EventHistoryDto.acked(new EventAck(eventId, code.orElse(null), now, userId)))
                            .thenReturn(AckResult.ACKED);
                })
                // 0행일 때만 사유 조회 — 정상 확인은 쿼리 한 번으로 끝난다
                .switchIfEmpty(Mono.defer(() -> eventWriter.ackState(eventId)
                        .map(st -> {
                            if (st.acked()) {
                                log.debug("[EventAck] 이미 확인된 이벤트. eventId={}", eventId);
                                return AckResult.ALREADY_ACKED;
                            }
                            if (st.sopEnabled() && sopResultJson == null) {
                                log.warn("[EventAck] 확인 거절 — SOP 이벤트인데 sopResult 없음. eventId={}, userId={}", eventId, userId);
                                return AckResult.REJECTED_SOP_RESULT_REQUIRED;
                            }
                            log.warn("[EventAck] 확인 UPDATE 0행(사유 불명, 동시 확인 경합 가능). eventId={}", eventId);
                            return AckResult.ALREADY_ACKED;
                        })
                        .defaultIfEmpty(AckResult.NOT_FOUND)))
                .onErrorResume(e -> {
                    log.error("[EventAck] 확인 처리 실패. eventId={}: {}", eventId, e.getMessage());
                    return Mono.just(AckResult.FAILED);
                });
    }
}
