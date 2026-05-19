package com.mpole.hana.event.service;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.event.websocket.WebsocketBroadcastService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {
    private final Sinks.Many<List<DeviceBundle>> centralSink;
    private final WebsocketBroadcastService websocketBroadcastService;

    @PostConstruct
    public void startEventPipeline() {
        centralSink.asFlux()
                .publishOn(Schedulers.newParallel("event-thread"))
                .flatMapIterable(bundles->bundles)
                .filter(deviceBundle ->
                        deviceBundle.points().stream().anyMatch(p->"Y".equalsIgnoreCase(p.alarmYn()) && "1".equals(p.valueRaw())))
                .flatMap(bundles->{
                    log.info("들어온값: -{}",bundles);

                    return websocketBroadcastService.publish(bundles);
                })
                .subscribe();
    }
}
