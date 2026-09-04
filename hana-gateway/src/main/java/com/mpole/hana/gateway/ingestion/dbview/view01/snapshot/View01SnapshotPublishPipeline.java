package com.mpole.hana.gateway.ingestion.dbview.view01.snapshot;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.gateway.ingestion.dbview.view01.View01ReadService;
import com.mpole.hana.gateway.pubsub.kafka.KafkaMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * <b>뷰 #01 스냅샷 파이프라인 1사이클</b> — 값 적용 → 변경 장비만 전체 포인트로 발행.
 *
 * <p>기존 델타 파이프라인({@code View01PublishPipeline})과 달리 팬아웃·groupingBy·정렬 단계가 없다.
 * 뷰 행은 이미 만들어진 골격의 값 칸에 쓰이기만 하고, 발행 대상은 dirty 표시로 결정된다.</p>
 *
 * <pre>
 *   read(전량) → store.apply(행마다 값 write, 바뀐 장비 dirty 표시)
 *              → store.drainDirtyBundles(dirty 장비의 "전체 포인트" 번들)
 *              → Kafka 발행
 * </pre>
 *
 * <p>읽기는 기존 {@link View01ReadService} 를 그대로 재사용한다(수정하지 않음).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "hdt.dbview.view01.snapshot", name = "enabled", havingValue = "true")
public class View01SnapshotPublishPipeline {

    private final View01ReadService readService;
    private final DeviceSnapshotStore store;
    private final KafkaMessagePublisher kafkaPublisher;

    public Mono<Void> runOneCycle() {
        long start = System.currentTimeMillis();
        OffsetDateTime collectedAt = OffsetDateTime.now();

        return readService.readFromViewTable()
                .subscribeOn(Schedulers.boundedElastic())
                // 1단계: 값 적용 — 객체를 새로 만들지 않고 기존 slot 에 write 만 한다.
                .doOnNext(store::apply)
                .then(Mono.fromCallable(() -> store.drainDirtyBundles(collectedAt)))
                .flatMapMany(bundles -> {
                    if (bundles.isEmpty()) {
                        log.info("[Snapshot] 변경 장비 없음 ({}ms)", System.currentTimeMillis() - start);
                        //return Mono.<Void>empty();
                        return Flux.empty();
                    }
                    log.info("[Snapshot] 변경 장비 {}대 발행 ({}ms)", bundles.size(), System.currentTimeMillis() - start);
                    //return Mono.empty();
                    //return publish(bundles);
                    return Flux.fromIterable(bundles);
                })
                .as(kafkaPublisher::publishAll)
                .doOnComplete(() -> log.info("3. [Snapshot] publish 시간: {}ms", System.currentTimeMillis() - start))
                .then();
    }

    private Mono<Void> publish(List<DeviceBundle> bundles) {
        return kafkaPublisher.publishAll(Flux.fromIterable(bundles))
                .publishOn(Schedulers.boundedElastic())
                .then();
    }

    /** 스케줄러에서 호출 — 사이클 실패가 스케줄을 죽이지 않도록 로깅 후 흡수한다(다음 주기 재시도). */
    public Mono<Void> runOneCycleSafely() {
        return runOneCycle()
                .doOnError(e -> log.error("[Snapshot] 사이클 실패(다음 주기 재시도): {}", e.getMessage(), e))
                .onErrorResume(e -> Mono.empty());
    }
}
