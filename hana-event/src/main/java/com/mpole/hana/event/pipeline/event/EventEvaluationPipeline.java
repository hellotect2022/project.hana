package com.mpole.hana.event.pipeline.event;

import com.mpole.hana.common.constant.NotificationChannel;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.EventClear;
import com.mpole.hana.common.dto.EventOccurrence;
import com.mpole.hana.event.config.sink.ReactiveQueue;
import com.mpole.hana.event.dto.EventHistoryDto;
import com.mpole.hana.event.pipeline.event.dto.ActiveEvent;
import com.mpole.hana.event.pipeline.event.dto.EvalOutcome;
import com.mpole.hana.event.websocket.WebsocketBroadcastService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 이벤트 평가 전용 병렬 파이프라인.
 *
 * <p>{@link ReactiveQueue#getDeviceRawFlux()} 를 <b>독립적으로 구독</b>한다.
 * 같은 싱크를 구독하는 CurrentValueService(현재값 upsert)/ValueHistoryService(값 이력) 와
 * 완전히 분리되어 있어 서로 회귀 영향을 주지 않는다(멀티캐스트 싱크 공유 소비).</p>
 *
 * <p>흐름: rawFlux → flatMapIterable(bundles) → evaluate(규칙 매칭/조건/Active 축) →
 * flatMapIterable(outcomes) → 발생이면 정본 INSERT + 액션 기록 + 웹소켓 발행,
 * 해소면 정본 UPDATE + 웹소켓 해소 통지. 캐시 미준비 시 빈 인덱스로 매칭 0 → 무발행.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventEvaluationPipeline {

    private final ReactiveQueue reactiveQueue;
    private final EventEvaluator evaluator;
    private final ActiveEventStore activeStore;
    private final WebsocketBroadcastService websocketBroadcastService;
    private final EventWriter eventWriter;

    /** 전이마다 활성맵 격자를 INFO 로 찍을지(개발자용, 2026-10-06). 기본 true. */
    @Value("${hdt.event.dump-active-on-change:true}")
    private boolean dumpActiveOnChange;

    @PostConstruct
    public void start() {
        log.info("[EventEvaluationPipeline] 이벤트 평가 파이프라인 시작");
        reactiveQueue.getDeviceRawFlux()
                .doOnComplete(()->log.info("event 평가를 위한 데이타 수신받은 "))
                // 평가/발행은 별도 스케줄러에서. 기존 event/snapshot 스레드와 격리.
                .publishOn(Schedulers.newParallel("event-eval-thread"))
                // 2026-10-06: 번들을 시간 오름차순으로. View01 컨슈머는 현재값(최신 우선)을 위해 timestamp 역순으로 흘리지만,
                // 상태 전이는 시간순이어야 한다. 역순이면 같은 포인트의 "이상→정상" 이 "정상→이상" 으로 평가돼 알람이 뜬 채 남는다.
                .flatMapIterable(bundles -> bundles.stream()
                        .sorted(Comparator.comparing(DeviceBundle::timestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList())
                // 2026-10-06: flatMap → concatMap. 같은 키의 발생·해소·재활성이 순서대로 평가·커밋돼야 메모리와 DB 가 어긋나지 않는다.
                // (병렬이면 해소 UPDATE 보다 재활성 UPDATE 가 먼저 커밋돼 0행 → 슬롯 해제 → 열린 행 고아 → 그 키 INSERT 영구 차단)
                // 전이는 초당 몇 건이라 직렬화해도 처리량 영향은 없다.
                .concatMap(bundle -> Mono
                        .fromCallable(() -> evaluator.evaluate(bundle))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(e -> {
                            log.error("[EventEvaluationPipeline] 평가 에러(해당 번들 skip): {}", e.getMessage());
                            return Mono.just(List.<EvalOutcome>of());
                        }))
                .flatMapIterable(outcomes -> outcomes)
                .doOnNext(data->log.info("[EventEvaluationPipeline] 들어온 값: {}", data))
                .concatMap(outcome -> handle(outcome)
                        .onErrorResume(e -> {
                            log.error("[EventEvaluationPipeline] 처리 에러(해당 건 skip): {}", e.getMessage());
                            return Mono.empty();
                        })
                        // 개발자용(2026-10-06): 전이 1건 처리할 때마다 활성맵 전체를 격자로 찍는다. hdt.event.dump-active-on-change=false 로 끔.
                        .then(Mono.fromRunnable(this::dumpActiveIfEnabled)))
                .retryWhen(
                        Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5))
                                .maxBackoff(Duration.ofSeconds(30))
                                .jitter(0.5)
                                .doBeforeRetry(rs -> log.warn(
                                        "[EventEvaluationPipeline] 스트림 재시도. attempt={}, cause={}",
                                        rs.totalRetries() + 1, rs.failure().toString()))
                )
                // 2026-10-01 구독 활성화 — 이게 없으면 평가 파이프라인이 아예 돌지 않는다(규칙 문서 §8 1번).
                .subscribe(
                        null,
                        error -> log.error("[EventEvaluationPipeline] 치명적 에러: ", error),
                        () -> log.info("[EventEvaluationPipeline] 스트림 종료")
                );
    }

    private Mono<Void> handle(EvalOutcome outcome) {
        if (outcome == null) return Mono.empty();
        if (outcome.reactivation()) return emitReactivation(outcome.occurrence());
        return outcome.isOccurrence()
                ? emitOccurrence(outcome.occurrence(), outcome.stateless())
                : emitClear(outcome.clear());
    }

    /**
     * 재활성 처리(2026-10-02 B안) — 해소·미확인 이벤트에 조건이 다시 성립.
     * 정본은 INSERT 가 아니라 UPDATE(cleared_at=null, occurrence_count+1)이고,
     * 웹소켓으로 <b>같은 eventId</b> 의 발생 봉투를 다시 보낸다(클라이언트는 새 팝업이 아니라 기존 알람을 다시 켠다).
     *
     * <p>DB 에 대상 행이 없거나(이미 확인됐거나 상태가 어긋남) 유니크 위반(같은 키의 다른 열린 행)이면 메모리와 DB 가 어긋난 것이다.
     * 2026-10-06: 슬롯을 그냥 풀지 않고 {@link ActiveEventStore#resync} 로 DB 를 읽어 맵을 맞춘다. 풀기만 하면 열린 행이 고아가 돼
     * 해소도 안 되고 INSERT 도 매번 막힌다.</p>
     */
    private Mono<Void> emitReactivation(EventOccurrence ev) {
        return eventWriter.reactivate(ev.eventId(), ev.occurredAt(), ev.valueRaw())
                .flatMap(count -> {
                    log.info("[EventEvaluationPipeline] 이벤트 재활성. eventId={}, ruleId={}, deviceId={}, count={}",
                            ev.eventId(), ev.ruleId(), ev.deviceId(), count);
                    return websocketBroadcastService
                            .publish(EventHistoryDto.occurrence(ev.toBuilder().occurrenceCount(count).build()))
                            .thenReturn(count);
                })
                .switchIfEmpty(resyncAfterMismatch(ev, "재활성 대상 행 없음(이미 확인됐거나 DB 와 불일치)").then(Mono.empty()))
                .onErrorResume(e -> {
                    log.error("[EventEvaluationPipeline] 재활성 실패. eventId={}: {}", ev.eventId(), e.getMessage());
                    return resyncAfterMismatch(ev, "재활성 UPDATE 실패(같은 키의 다른 열린 행 가능성)").then(Mono.empty());
                })
                .then();
    }

    /**
     * 메모리↔DB 어긋남이 드러났을 때 — DB 를 읽어 맵을 맞춘다(2026-10-06). 결과는 {@link ActiveEventStore.Resync} 참고.
     * 재동기화 자체가 실패하면(DB 장애) 슬롯을 풀어 두는 것으로 후퇴한다 — 다음 성립이 다시 INSERT 를 시도하며 그때 또 맞춘다.
     */
    private Mono<ActiveEventStore.Resync> resyncAfterMismatch(EventOccurrence ev, String reason) {
        log.warn("[EventEvaluationPipeline] 메모리↔DB 불일치 — {}. eventId={}, ruleId={}, deviceId={}, pointId={} → 재동기화",
                reason, ev.eventId(), ev.ruleId(), ev.deviceId(), ev.pointId());
        return activeStore.resync(ev.ruleId(), ev.deviceId(), ev.pointId())
                .onErrorResume(e -> {
                    releaseSlot(ev, "재동기화 실패(" + e.getMessage() + ")");
                    return Mono.empty();
                });
    }

    /**
     * 발생 처리.
     * <ol>
     *   <li>정본({@code tbl_event}) INSERT — 유니크 충돌로 무시되면 인메모리 활성 등록도 되돌린다</li>
     *   <li>웹소켓 발행(eventType={event_code})</li>
     * </ol>
     * <p>채널별 액션 행({@code tbl_event_action})은 2026-10-02 폐기 — 켜진 채널은 {@code outputs} 스냅샷에 있고,
     * 클라이언트가 그걸 보고 팝업·SOP·CCTV 를 연다. 서버가 채널별 상태를 따로 추적하지 않는다.</p>
     * <p>INSERT 가 DB 에 막히면(같은 키의 열린 행이 이미 있음) 메모리가 DB 를 모르고 있던 것이다. 2026-10-06: 슬롯을 풀기만 하지 않고
     * {@link ActiveEventStore#resync} 로 그 열린 행을 맵에 들여오거나(미확인), 확인된 행이면 닫고 INSERT 를 한 번 재시도한다.
     * 풀기만 하면 그 키는 해소도 안 되고 INSERT 도 매번 막혀 영원히 알람을 못 띄운다.
     * INSERT 자체가 예외로 실패하면 슬롯을 푼다. 사건형({@code stateless})은 슬롯을 잡은 적이 없으므로 풀지 않는다.</p>
     */
    private Mono<Void> emitOccurrence(EventOccurrence ev, boolean stateless) {
        return emitOccurrence(ev, stateless, true);
    }

    private Mono<Void> emitOccurrence(EventOccurrence ev, boolean stateless, boolean retryAfterClose) {
        return eventWriter.insert(ev, stateless)
                .flatMap(inserted -> {
                    if (!inserted) {
                        if (stateless) return Mono.empty();
                        return resyncAfterMismatch(ev, "활성 중복(DB 차단)")
                                .flatMap(r -> (r.action() == ActiveEventStore.ResyncAction.CLOSED_ACKED && retryAfterClose
                                        && activeStore.tryOccur(toActiveEntry(ev)))
                                        ? emitOccurrence(ev, false, false)   // 확인됐는데 열려 있던 행을 닫았으니 한 번만 재시도
                                        : Mono.empty());
                    }
                    logChannels(ev);
                    return websocketBroadcastService.publish(EventHistoryDto.occurrence(ev));
                })
                .onErrorResume(e -> {
                    if (stateless) {
                        log.warn("[EventEvaluationPipeline] 사건형 정본 저장 실패(유실). eventId={}, ruleId={}: {}",
                                ev.eventId(), ev.ruleId(), e.getMessage());
                    } else {
                        releaseSlot(ev, "정본 저장 실패: " + e.getMessage());
                    }
                    return Mono.empty();
                })
                .then();
    }

    /** 해소 처리 — 정본 UPDATE 후 웹소켓으로 해소 통지(클라이언트가 알람을 내릴 근거). */
    private Mono<Void> emitClear(EventClear ev) {
        return eventWriter.clear(ev)
                .flatMap(updated -> updated
                        ? websocketBroadcastService.publish(EventHistoryDto.cleared(ev))
                        : Mono.empty())
                .then();
    }

    /** 활성맵 격자 로그 — 운영에서 활성 수백 건이면 로그가 커지므로 yml 로 끌 수 있다. */
    private void dumpActiveIfEnabled() {
        if (dumpActiveOnChange) {
            log.info("[EventEvaluationPipeline] 활성맵 현황\n{}", activeStore.dumpTable());
        }
    }

    /** INSERT 재시도용 활성 항목 — 평가기가 만든 것과 같은 모양(포인트 표시명은 발생 스냅샷에 없어 tagName 으로 대신). */
    private static ActiveEvent toActiveEntry(EventOccurrence ev) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ActiveEvent(ev.eventId(), ev.ruleId(), ev.deviceId(), ev.pointId(), ev.level(), false,
                ev.ruleName(), ev.eventCode(), ev.deviceKey(), ev.tagName(), ev.tagName(), ev.occurredAt(), now);
    }

    private void releaseSlot(EventOccurrence ev, String reason) {
        activeStore.release(ev.ruleId(), ev.deviceId(), ev.pointId());
        log.warn("[EventEvaluationPipeline] 발생 취소 — {}. eventId={}, ruleId={}, deviceId={}",
                reason, ev.eventId(), ev.ruleId(), ev.deviceId());
    }

    /** 발생 로그 — 켜진 출력 채널은 outputs 스냅샷에서 읽는다(채널별 상태 추적은 없다). */
    private void logChannels(EventOccurrence ev) {
        List<NotificationChannel> channels = ev.outputs() == null ? List.of() : ev.outputs().enabledChannels();
        log.info("[EventEvaluationPipeline] 이벤트 발생. eventId={}, code={}, level={}, deviceId={}, channels={}",
                ev.eventId(), ev.eventCode(), ev.level(), ev.deviceId(), channels);
    }
}
