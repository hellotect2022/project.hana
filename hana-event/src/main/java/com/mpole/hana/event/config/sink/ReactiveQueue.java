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
    //    구독자(CurrentValue/ValueHistory/EventEvaluation)가 기동 시 붙어 영원히 안 떨어지므로 autoCancel 기본값이어도 안전.
    //    (autoCancel=true 는 "마지막 구독자가 떠나면 sink 종료" — 아래 2번이 그걸로 죽었다)
    private final Sinks.Many<List<DeviceBundle>> deviceRawSink =
            Sinks.many().multicast().onBackpressureBuffer(20000, false);

    // 2. 웹소켓 발행용 싱크 (이벤트 발생/해소 봉투)
    //    ★ 2026-10-01 버그 수정: multicast().onBackpressureBuffer(n) 은 autoCancel=true 라서
    //      마지막 웹소켓 세션이 끊기는 순간 sink 가 종료됐다. 이후 emit 은 전부 FAIL_CANCELLED 로 조용히 버려지고,
    //      새로 접속한 세션은 영원히 아무것도 못 받았다(테스트 서버 15:54 disconnect 뒤 증상).
    //      directBestEffort = 지금 붙어 있는 구독자에게만 전달, 없으면 버림, 구독자가 오가도 종료되지 않음.
    //      웹소켓은 "지금 보고 있는 화면" 용이고 정본은 tbl_event 라 버려져도 손실이 아니다.
    private final Sinks.Many<EventHistoryDto> eventHistorySink =
            Sinks.many().multicast().directBestEffort();


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
