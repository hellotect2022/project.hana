package com.mpole.hana.event.config.sink;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.event.dto.EventHistoryDto;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

@Component
public class ReactiveQueue {
    // 1. 센서/디바이스 원본 데이터 스트림 싱크
    private final Sinks.Many<List<DeviceBundle>> deviceRawSink =
            Sinks.many().multicast().onBackpressureBuffer(20000);

    // 2. 이벤트 체크 통과 후 히스토리 DB 저장용 스트림 싱크
    private final Sinks.Many<EventHistoryDto> eventHistorySink =
            Sinks.many().multicast().onBackpressureBuffer(20000);


    /* ========================================================
     * [발행 창구 (Emit Methods)]
     * 외부 로직에서는 이 메서드를 통해 안전하게 데이터를 주입합니다.
     * ======================================================== */

    public Sinks.EmitResult emitDeviceBundle(List<DeviceBundle> bundle) {
        // 초당 만 건이므로 busy-loop나 데드락을 방지하기 위해
        // tryEmitNext 후 결과 처리를 세부적으로 할 수도 있습니다.
        return this.deviceRawSink.tryEmitNext(bundle);
    }

    public Sinks.EmitResult emitEventHistory(EventHistoryDto history) {
        return this.eventHistorySink.tryEmitNext(history);
    }

    /* ========================================================
     * [구독 창구 (Stream Accessors)]
     * 소비하는 쪽(WebSocket, DB Consumer)에서는 Flux 형태로만 가져갑니다.
     * ======================================================== */

    public Flux<List<DeviceBundle>> getDeviceRawFlux() {
        return this.deviceRawSink.asFlux();
    }

    public Flux<EventHistoryDto> getEventHistoryFlux() {
        return this.eventHistorySink.asFlux();
    }
}
