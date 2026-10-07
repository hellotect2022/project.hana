package com.mpole.hana.event.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.event.pipeline.event.EventAckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

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
        Mono<Void> input = session.receive()
                .doOnSubscribe(s -> log.info("Websocket connected. sessionId={}", session.getId()))
                .map(message -> message.getPayloadAsText())
                .doOnNext(text -> log.info("Client message: {}", text))
                // 한 메시지 처리 실패가 세션을 끊지 않도록 각각 흡수한다
                .filter(this::clientResponseFilter)
                .flatMap(text -> handleClientMessage(text, session.getId()))
                .doOnComplete(() -> log.info("Websocket disconnected. sessionId={}", session.getId()))
                .doOnError(e -> log.info("Websocket error {}", e.getMessage()))
                .then();

        Mono<Void> output = session.send(
                broadcaster.streamJson()
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

    /** 클라이언트 → 서버 메시지. 지금은 ACK 한 종류다. 형식이 틀리면 로그만 남긴다. */
    private Mono<Void> handleClientMessage(String text, String sessionId) {
        try {
            JsonNode node = objectMapper.readTree(text);
            String type = node.path("type").asText("");
            if (!"ACK".equalsIgnoreCase(type)) {
                log.debug("[WebSocket] 알 수 없는 클라이언트 메시지 무시. sessionId={}, type={}", sessionId, type);
                return Mono.empty();
            }
            UUID eventId = UUID.fromString(node.path("eventId").asText());
            Long userId = node.hasNonNull("userId") ? node.get("userId").asLong() : null;
            return ackService.ack(eventId, userId);
        } catch (Exception e) {
            log.warn("[WebSocket] 클라이언트 메시지 파싱 실패. sessionId={}, text={}: {}", sessionId, text, e.getMessage());
            return Mono.empty();
        }
    }
}
