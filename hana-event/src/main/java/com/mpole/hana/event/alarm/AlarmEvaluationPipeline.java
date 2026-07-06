package com.mpole.hana.event.alarm;

import com.fasterxml.uuid.Generators;
import com.mpole.hana.common.dto.AlarmEvent;
import com.mpole.hana.event.config.ReactiveEventQueue;
import com.mpole.hana.event.dto.EventHistoryDto;
import com.mpole.hana.event.websocket.WebsocketBroadcastService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * 알람 평가 전용 병렬 파이프라인.
 *
 * <p>{@link ReactiveEventQueue#getDeviceRawFlux()} 를 <b>독립적으로 구독</b>한다.
 * EventService(WebSocket)/SnapshotService(DB upsert) 구독과 완전히 분리되어 있어
 * 기존 파이프라인에 회귀 영향을 주지 않는다(멀티캐스트 싱크 공유 소비).</p>
 *
 * <p>흐름: rawFlux → flatMapIterable(bundles) → evaluate(정책 매칭/조건/상태) →
 * flatMapIterable(alarms) → EventHistoryDto(eventType="ALARM") 로 감싸 기존 event 싱크로 emit.
 * 이후 WebSocket 전송(streamJson) 및 스냅샷 파이프라인(EventSnapshotService)으로 자동 전파된다.
 * 캐시 미준비 시 빈 인덱스로 매칭 0 → 무발행.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmEvaluationPipeline {

    private final ReactiveEventQueue reactiveEventQueue;
    private final AlarmEvaluator evaluator;
    private final WebsocketBroadcastService websocketBroadcastService;

    @PostConstruct
    public void start() {
        log.info("[AlarmEvaluationPipeline] 알람 평가 파이프라인 시작");
        reactiveEventQueue.getDeviceRawFlux()
                // 평가/발행은 별도 스케줄러에서. 기존 event/snapshot 스레드와 격리.
                .publishOn(Schedulers.newParallel("alarm-eval-thread"))
                .flatMapIterable(bundles -> bundles)
                .flatMap(bundle -> Mono
                        .fromCallable(() -> evaluator.evaluate(bundle))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("[AlarmEvaluationPipeline] 평가 에러(해당 번들 skip): {}", e.getMessage());
                            return Mono.just(List.<AlarmEvent>of());
                        }))
                .flatMapIterable(alarms -> alarms)
                .flatMap(alarm -> emit(alarm)
                        .onErrorResume(e -> {
                            log.error("[AlarmEvaluationPipeline] emit 에러(해당 알람 skip): {}", e.getMessage());
                            return Mono.empty();
                        }))
                .retryWhen(
                        Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5))
                                .maxBackoff(Duration.ofSeconds(30))
                                .jitter(0.5)
                                .doBeforeRetry(rs -> log.warn(
                                        "[AlarmEvaluationPipeline] 스트림 재시도. attempt={}, cause={}",
                                        rs.totalRetries() + 1, rs.failure().toString()))
                )
                .subscribe(
                        null,
                        error -> log.error("[AlarmEvaluationPipeline] 치명적 에러: ", error),
                        () -> log.info("[AlarmEvaluationPipeline] 스트림 종료")
                );
    }

    /**
     * 발생 알람을 {@link EventHistoryDto}(eventType="ALARM") 로 감싸 기존 이벤트 싱크로 emit 한다.
     * 기존 FIRE_BELL 과 동일한 싱크를 공유하므로 WebSocket 전송·스냅샷 저장으로 자동 전파된다.
     */
    private Mono<Void> emit(AlarmEvent alarm) {
        if (alarm == null) return Mono.empty();

        EventHistoryDto dto = EventHistoryDto.builder()
                .eventId(Generators.timeBasedEpochGenerator().generate())
                .eventType("ALARM")
                .isProcessed(false)
                .alarm(alarm)
                .build();

        return websocketBroadcastService.publish(dto);
    }
}
