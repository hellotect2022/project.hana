package com.mpole.hana.gateway.ingestion.dbview;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.gateway.config.properties.HdtPollingProperties;
import com.mpole.hana.gateway.pubsub.kafka.KafkaMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.SenderResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class DbViewPublishPipeline {

    private final AtomicInteger bundleCounter = new AtomicInteger(0);
    private final AtomicLong cycleStartMs = new AtomicLong(0);

    private final HdtPollingProperties pollingProperties;
    private final DbViewReadService readService;
    private final DbViewChangeFilter changeFilter;
    private final DbViewBundleService bundleService;
    private final KafkaMessagePublisher kafkaPublisher;


    public int getAndResetCount() {
        return bundleCounter.getAndSet(0);
    }

    public Mono<Void> runOneCycle() {
        cycleStartMs.set(System.currentTimeMillis());

        return readService
                    // 1단계 데이타 수집
                    .readFromViewTable()
                    .collectList()
                    //.doOnSuccess(data->log.info("collectList {}개", data.size()))
                    .doOnSuccess(data->log.info("1. collectList 시간: {}ms", System.currentTimeMillis()-cycleStartMs.get()))
                    .subscribeOn(Schedulers.boundedElastic()) // I/0 전용 작업 스케줄러
                    // 2단계 bundling 작업
                    .publishOn(Schedulers.parallel()) // cpu 집약적인 계산
                    .flatMapMany(list -> {
                        //log.info("번들링 시작 전 리스트 크기: {}", list.size());
                        //log.info("데이타 사이즈 : {}",list.toString().getBytes().length);
                        // 1.5단계 변경 감지(delta) — 풀리드 결과에서 직전 폴링 대비 실제로 바뀐 포인트만 통과.
                        //   뷰 update_datetime 이 단조증가하지 않아 워터마크 증분쿼리는 누락 발생 → 풀리드 유지 + 앱레벨 diff.
                        //   value/alarmYn 변화만 통과(순수 timestamp touch 는 제외)하여 하류 부하를 실변경 수준으로 축소.
                        List<XnPointData> changed = changeFilter.retainChanged(list);
                        log.info("1.5 변경 감지: 전체 {}개 중 변경 {}개 통과 ({}ms)",
                                list.size(), changed.size(), System.currentTimeMillis() - cycleStartMs.get());
                        return bundleService.bundle(Flux.fromIterable(changed));
                    })
                    .doOnNext(b -> {
                        bundleCounter.incrementAndGet();
                    })
                    .doOnComplete(()->log.info("2. bundle 시간: {}ms",System.currentTimeMillis()-cycleStartMs.get()))
                     // 3단계 kafka publish
                    .publishOn(Schedulers.boundedElastic()) // 네트워크 I/O
                    //.window(100)
                    //.flatMap(a-> kafkaPublisher.publishAll(a),1)
                    .as(kafkaPublisher::publishAll)
                    .doOnComplete(()->log.info("3. publish 시간: {}ms",System.currentTimeMillis()-cycleStartMs.get()))
                    .doOnError(e -> log.error("처리 중 에러: ", e))
                    .then()
                    ;


    }
}
