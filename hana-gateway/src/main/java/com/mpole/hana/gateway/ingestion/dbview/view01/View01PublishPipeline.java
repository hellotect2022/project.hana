package com.mpole.hana.gateway.ingestion.dbview.view01;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.gateway.pubsub.kafka.KafkaMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <b>외부 뷰 #01 파이프라인 1사이클</b> — 값 수집 → 변경감지 → 장비 번들링 → Kafka 발행.
 *
 * <p>단계별 스케줄러를 나눠 쓴다: 조회는 I/O({@code boundedElastic}), 번들링은 CPU({@code parallel}),
 * 발행은 다시 네트워크 I/O.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class View01PublishPipeline {

    private final AtomicInteger bundleCounter = new AtomicInteger(0);
    private final AtomicLong cycleStartMs = new AtomicLong(0);

    private final View01ReadService readService;
    private final View01ChangeFilter changeFilter;
    private final View01BundleService bundleService;
    private final KafkaMessagePublisher kafkaPublisher;

    public int getAndResetCount() {
        return bundleCounter.getAndSet(0);
    }

    public Mono<Void> runOneCycle() {
        cycleStartMs.set(System.currentTimeMillis());

        // 1.5단계 변경 감지(delta) — 풀리드 결과에서 직전 폴링 대비 실제로 바뀐 포인트만 통과.
        //   뷰 update_datetime 이 단조증가하지 않아 워터마크 증분쿼리는 누락 발생 → 풀리드 유지 + 앱레벨 diff.
        //List<XnPointData> changed = changeFilter.retainChanged(list);
        //log.info("1.5 변경 감지: 전체 {}개 중 변경 {}개 통과 ({}ms)", list.size(), changed.size(), System.currentTimeMillis() - cycleStartMs.get());
        return readService
                    // 1단계 데이타 수집
                    .readFromViewTable()
                    .subscribeOn(Schedulers.boundedElastic()) // I/0 전용 작업 스케줄러
                    .collectList()
                    .doOnSuccess(data -> log.info("1. collectList 시간: {}ms", System.currentTimeMillis() - cycleStartMs.get()))
                    // 2단계 bundling 작업
                    .publishOn(Schedulers.parallel()) // cpu 집약적인 계산
                    .flatMapMany(bundleService::bundle)
                    .doOnNext(b -> bundleCounter.incrementAndGet())
                    .doOnComplete(() -> log.info("2. bundle 갯수: {}개, 소요 시간: {}ms",bundleCounter.getAndSet(0),System.currentTimeMillis() - cycleStartMs.get()))
                    // 3단계 kafka publish
                    .publishOn(Schedulers.boundedElastic()) // 네트워크 I/O
                    .as(kafkaPublisher::publishAll)
                    .doOnComplete(() -> log.info("3. publish 시간: {}ms", System.currentTimeMillis() - cycleStartMs.get()))
                    .then();
    }

    /** 스케줄러에서 호출 — 사이클 실패가 스케줄을 죽이지 않도록 로깅 후 흡수한다(다음 주기 재시도). */
    public Mono<Void> runOneCycleSafely() {
        return runOneCycle()
                .doOnError(e -> log.error("[View01] 사이클 실패(다음 주기 재시도): {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }
}
