package com.mpole.hana.event.pipeline.event;

import com.mpole.hana.common.constant.ControlTrigger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.UUID;

/**
 * 이벤트 연동 디바이스 제어 호출 — event 서버 → digitaltwin 내부 API (2026-10-12).
 *
 * <p>event 서버는 발생·확인 시점을 알지만 제어 로직(제어포인트·XN 어댑터)은 digitaltwin 에 있다.
 * 그래서 {@code POST {base}/internal/event/{eventId}/execute-control?trigger=…} 로 실행을 맡긴다.
 * digitaltwin 이 그 이벤트의 outputs 를 DB 에서 읽어 해당 시점의 켜진 제어만 실행한다.</p>
 *
 * <p><b>fire-and-forget</b> — 파이프라인은 {@code concatMap} 직렬이라 여기서 기다리면 제어 응답(XN 연결·읽기 타임아웃)만큼
 * 다음 이벤트 평가가 밀린다. 그래서 별도 구독으로 던지고 결과는 로그로만 남긴다. 실패해도 이벤트 기록·웹소켓 통지에는 영향이 없다.
 * 재시도는 하지 않는다 — 장비 제어를 두 번 보내는 게 한 번 놓치는 것보다 위험할 수 있어서다(놓친 건 관제자가 수동 제어).</p>
 */
@Slf4j
@Component
public class EventControlClient {

    private final WebClient webClient;
    private final boolean enabled;
    private final Duration timeout;

    public EventControlClient(
            WebClient.Builder builder,
            @Value("${hdt.event.control.enabled:true}") boolean enabled,
            @Value("${hdt.event.control.digitaltwin-base-url:http://localhost:8082}") String baseUrl,
            @Value("${hdt.event.control.timeout:15s}") Duration timeout
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.enabled = enabled;
        this.timeout = timeout;
        log.info("[EventControl] 디바이스 제어 연동 enabled={}, digitaltwin={}, timeout={}", enabled, baseUrl, timeout);
    }

    /** 해당 시점 제어 실행을 digitaltwin 에 요청한다(비동기, 결과는 로그). */
    public void fire(UUID eventId, ControlTrigger trigger) {
        if (!enabled || eventId == null) return;
        webClient.post()
                .uri("/internal/event/{eventId}/execute-control?trigger={trigger}", eventId, trigger.name())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        body -> log.info("[EventControl] 제어 요청 완료. eventId={}, trigger={}, 응답={}", eventId, trigger, body),
                        e -> log.error("[EventControl] 제어 요청 실패(이벤트 기록엔 영향 없음, 수동 제어 필요). eventId={}, trigger={}: {}",
                                eventId, trigger, e.getMessage())
                );
    }
}
