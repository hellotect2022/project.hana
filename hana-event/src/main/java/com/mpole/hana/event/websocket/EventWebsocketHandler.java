package com.mpole.hana.event.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.event.dto.EventHistoryDto;
import com.mpole.hana.event.pipeline.event.EventAckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.UUID;

/**
 * 웹소켓 {@code /ws/event} 핸들러.
 *
 * <ul>
 *   <li><b>송신</b> — {@link WebsocketBroadcastService#streamJson()} 의 발생·해소·확인 봉투를 세션에 흘려보낸다.</li>
 *   <li><b>수신</b> — 클라이언트의 확인(ack) 메시지 {@code {"type":"ACK","eventId":"…","userId":12}} 를
 *       {@link EventAckService} 로 넘긴다(2026-10-02). 그 외 메시지는 무시한다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventWebsocketHandler implements WebSocketHandler {

    private final WebsocketBroadcastService broadcaster;
    private final EventAckService ackService;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // 이 세션에만 보내는 응답 통로(2026-10-12) — 확인 거절(ACK_REJECTED)처럼 보낸 사람만 알아야 하는 메시지용.
        // 브로드캐스트 흐름과 merge 해 같은 session.send 로 나간다. 세션이 끝나면 함께 사라진다.
        Sinks.Many<String> direct = Sinks.many().unicast().onBackpressureBuffer();

        Mono<Void> input = session.receive()
                .doOnSubscribe(s -> log.info("Websocket connected. sessionId={}", session.getId()))
                .map(message -> message.getPayloadAsText())
                .doOnNext(text -> log.info("Client message: {}", text))
                // 한 메시지 처리 실패가 세션을 끊지 않도록 각각 흡수한다
                .filter(this::clientResponseFilter)
                .flatMap(text -> handleClientMessage(text, session.getId(), direct))
                .doOnComplete(() -> log.info("Websocket disconnected. sessionId={}", session.getId()))
                .doOnError(e -> log.info("Websocket error {}", e.getMessage()))
                .doFinally(sig -> direct.tryEmitComplete())
                .then();

        Mono<Void> output = session.send(
                Flux.merge(broadcaster.streamJson(), direct.asFlux())
                        .map(session::textMessage)
                        .onErrorResume(e -> {
                            log.error("WebSocket send error. sessionId={}", session.getId(), e);
                            return Mono.empty();
                        })
        );

        return output.and(input);
    }

    private boolean clientResponseFilter(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            return node.hasNonNull("type");
        } catch (Exception e) {
            //log.warn("[WebSocket] 클라이언트 메시지 파싱 실패. sessionId={}, text={}: {}", sessionId, text, e.getMessage());
            return false;
        }
    }

    /**
     * 클라이언트 → 서버 메시지. 지금은 ACK 한 종류다. 형식이 틀리면 로그만 남긴다.
     * <pre>{"type":"ACK","eventId":"…","userId":12,"sopResult":{…SOP 클릭 기록, 형식 자유…}}</pre>
     * SOP 이벤트인데 sopResult 가 없으면 확인하지 않고 이 세션에만 ACK_REJECTED 를 보낸다(2026-10-12).
     */
    private Mono<Void> handleClientMessage(String text, String sessionId, Sinks.Many<String> direct) {
        try {
            JsonNode node = objectMapper.readTree(text);
            String type = node.path("type").asText("");
            if (!"ACK".equalsIgnoreCase(type)) {
                log.debug("[WebSocket] 알 수 없는 클라이언트 메시지 무시. sessionId={}, type={}", sessionId, type);
                return Mono.empty();
            }
            UUID eventId = UUID.fromString(node.path("eventId").asText());
            Long userId = node.hasNonNull("userId") ? node.get("userId").asLong() : null;
            // sopResult 는 JSON 이면 무엇이든(객체·배열). 문자열로 직렬화해 jsonb 로 저장한다. 없거나 null 이면 null
            JsonNode sop = node.get("sopResult");
            String sopResultJson = (sop == null || sop.isNull()) ? null : objectMapper.writeValueAsString(sop);
            if (sopResultJson != null && sopResultJson.length() > MAX_SOP_RESULT_CHARS) {
                emitDirect(direct, EventHistoryDto.ackRejected(eventId,
                        "sopResult 가 너무 큽니다(최대 " + MAX_SOP_RESULT_CHARS / 1024 + "KB)."));
                return Mono.empty();
            }
            return ackService.ack(eventId, userId, sopResultJson)
                    .doOnNext(result -> {
                        if (result == EventAckService.AckResult.REJECTED_SOP_RESULT_REQUIRED) {
                            emitDirect(direct, EventHistoryDto.ackRejected(eventId, EventAckService.REASON_SOP_RESULT_REQUIRED));
                        }
                    })
                    .then();
        } catch (Exception e) {
            log.warn("[WebSocket] 클라이언트 메시지 파싱 실패. sessionId={}, text={}: {}", sessionId, text, e.getMessage());
            return Mono.empty();
        }
    }

    /** sopResult 크기 상한(문자 수, 약 256KB). 형식은 자유지만 무한정 받지 않는다. */
    private static final int MAX_SOP_RESULT_CHARS = 256 * 1024;

    private void emitDirect(Sinks.Many<String> direct, EventHistoryDto dto) {
        try {
            direct.tryEmitNext(objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            log.error("[WebSocket] 세션 응답 직렬화 실패. eventId={}: {}", dto.eventId(), e.getMessage());
        }
    }
}
