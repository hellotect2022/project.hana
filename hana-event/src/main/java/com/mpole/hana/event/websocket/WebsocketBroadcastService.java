package com.mpole.hana.event.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.event.config.sink.ReactiveQueue;
import com.mpole.hana.event.dto.EventHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebsocketBroadcastService {
    private final ObjectMapper objectMapper;

    //private final Sinks.Many<DeviceBundle> sink = Sinks.many().multicast().directBestEffort();
    private final ReactiveQueue reactiveQueue;

    public Mono<Void> publish(EventHistoryDto eventHistoryDto) {
        if (eventHistoryDto == null) {
            return Mono.empty();
        }

        var result = reactiveQueue.emitEventHistory(eventHistoryDto);

        // EmitResult 가 sink 상태를 그대로 말해 준다. 어느 경우든 Kafka/DB 처리에는 영향 주면 안 된다.
        switch (result) {
            case OK -> { }
            // 지금 화면을 보는 세션이 없을 뿐 — directBestEffort 의 정상 동작. 정본은 tbl_event 에 있다.
            case FAIL_ZERO_SUBSCRIBER -> log.debug("WebSocket publish skipped(구독자 없음). eventId={}", eventHistoryDto.eventId());
            // sink 가 종료된 상태. 이 뒤로는 어떤 세션도 영원히 못 받는다 — 2026-10-01 autoCancel 버그가 이 모양이었다.
            case FAIL_CANCELLED, FAIL_TERMINATED -> log.error("[WebSocket] sink 종료 상태 — 이후 모든 이벤트가 웹소켓으로 안 나간다. result={}, eventId={}", result, eventHistoryDto.eventId());
            default -> log.warn("WebSocket publish failed. result={}, eventId={}", result, eventHistoryDto.eventId());
        }

        return Mono.empty();
    }

    public Flux<String> streamJson() {
        return //sink.asFlux()
        reactiveQueue
                .getEventHistoryFlux()
                .doOnNext(a->log.info("sink 로 들어온값 {}",a))
                // 클라이언트 전송 주기 제한
                // 너무 자주 보내면 브라우저/네트워크가 먼저 병목됨
                //.sample(Duration.ofMillis(100))
                //.onBackpressureLatest()
                .map(this::toJson)
                .onErrorResume(e->{
                    log.error("WebSocket event stream error", e);
                    return Flux.empty();
                });
    }


    private String toJson(EventHistoryDto event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("WebSocket JSON 변환 실패", e);
            return "{\"type\":\"ERROR\",\"message\":\"json convert fail\"}";
        }
    }
}
