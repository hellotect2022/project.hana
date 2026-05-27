package com.mpole.hana.event.service;

import com.fasterxml.uuid.Generators;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.event.config.ReactiveEventQueue;
import com.mpole.hana.event.dto.EventHistoryDto;
import com.mpole.hana.event.websocket.WebsocketBroadcastService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {
    //private final Sinks.Many<List<DeviceBundle>> centralSink;
    private final ReactiveEventQueue reactiveEventQueue;
    private final WebsocketBroadcastService websocketBroadcastService;

    @PostConstruct
    public void startEventPipeline() {
        //centralSink.asFlux()
        reactiveEventQueue.getDeviceRawFlux()
                .publishOn(Schedulers.newParallel("event-thread"))
                .flatMapIterable(bundles->bundles)
                .filter(deviceBundle ->
                        deviceBundle.points().stream().anyMatch(p->"Y".equalsIgnoreCase(p.alarmYn()) && "1".equals(p.valueRaw())))
                .flatMap(bundles->{
                    log.info("들어온값: -{}",bundles);
                    return Mono.just(EventHistoryDto.builder()
                            .eventId(Generators.timeBasedEpochGenerator().generate())
                            .eventType("FIRE_BELL")
                            .isProcessed(false)
                            .data(bundles)
                            .build());
                })
                .flatMap(websocketBroadcastService::publish)
                .subscribe();
    }
}
