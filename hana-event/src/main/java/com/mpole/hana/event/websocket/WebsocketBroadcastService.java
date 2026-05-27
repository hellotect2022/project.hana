package com.mpole.hana.event.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.event.config.ReactiveEventQueue;
import com.mpole.hana.event.dto.EventHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebsocketBroadcastService {
    private final ObjectMapper objectMapper;

    //private final Sinks.Many<DeviceBundle> sink = Sinks.many().multicast().directBestEffort();
    private final ReactiveEventQueue reactiveEventQueue;

    public Mono<Void> publish(EventHistoryDto eventHistoryDto) {
        if (eventHistoryDto == null) {
            return Mono.empty();
        }

        var result = reactiveEventQueue.emitEventHistory(eventHistoryDto);

        if (result.isFailure()) {
            // 접속자가 없거나, 순간적으로 전송 불가한 경우
            // Kafka/DB 처리에는 영향 주면 안 됨
            log.debug("WebSocket publish skipped. result={}", result);
        }

        return Mono.empty();
    }

    public Flux<String> streamJson() {
        return //sink.asFlux()
        reactiveEventQueue
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
