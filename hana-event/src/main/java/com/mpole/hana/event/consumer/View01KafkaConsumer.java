package com.mpole.hana.event.consumer;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.event.config.sink.ReactiveQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.OrderComparator;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class View01KafkaConsumer implements SmartLifecycle {
    private boolean isRunning = false;

    private final KafkaReceiver<String, Object> view01KafkaReceiver;
    //private final Sinks.Many<List<DeviceBundle>> centralSink;
    private final ReactiveQueue reactiveQueue;

    AtomicLong lastReceiveTime = new AtomicLong(0);

    private void startKafkaPipeline() {
        view01KafkaReceiver.receive()
                // [중요] 1. 배압 전략을 '최신 데이터 유지'로 설정
                .onBackpressureLatest()
                // flux를 통해 받을 데이타 갯수를 정의 해당 값이 찬 후 DB 작업이 끝나기 전까지 kafka 에 새로 데이타 요청 안함
                .limitRate(9910)
                .bufferTimeout(9910, Duration.ofMillis(200))
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(recordList -> {
                    long now = System.currentTimeMillis();
                    long prev = lastReceiveTime.getAndSet(now);
                    if (prev > 0) {
                        long receiveInterval = now - prev;
                        log.info("[Kafka Metrics] 이전 batch 수신 후 다음 batch 수신까지 {}ms, batchSize={}",
                                receiveInterval,
                                recordList.size());
                    } else {
                        log.info("[Kafka Metrics] 첫 batch 수신, batchSize={}", recordList.size());
                    }
                    List<DeviceBundle> bundles = recordList.stream()
                            .map(record -> (DeviceBundle) record.value())
                            .sorted(Comparator.comparing(DeviceBundle::timestamp,Comparator.nullsLast(Comparator.reverseOrder())))
                            .toList();
                    log.debug("kafka 수신 데이타 : {}",bundles);
                    publishToEventPipeline(bundles);
                })
                .doOnError(e->log.error("kafka 수신하다가 에러남?",e))
                .retryWhen(
                        Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(5))
                                .maxBackoff(Duration.ofSeconds(30))
                                .jitter(0.5)
                                .doBeforeRetry(retrySignal -> {
                                    log.warn(
                                            "Kafka Receiver 재시도 중... attempt={}, cause={}",
                                            retrySignal.totalRetries() + 1,
                                            retrySignal.failure().toString()
                                    );
                                })
                )
                .subscribe(
                        null, // 정상 데이터 처리 (flatMap에서 이미 다 함)
                        error -> log.error("카프카 스트림 치명적 에러 발생: ", error), // 여기서 에러를 잡아줘야 서버가 안 죽음
                        () -> log.info("카프카 스트림 종료")
                );
        ;

    }

    private void publishToEventPipeline(List<DeviceBundle> bundles) {
        if (bundles == null || bundles.isEmpty()) {
            return;
        }

        var result = reactiveQueue.emitDeviceBundle(bundles);

        if (result.isFailure()) {
            log.warn("Event pipeline emit failed. result={}, size={}", result, bundles.size());
        }
    }


    @Override
    public void start() {
        log.info("초기화 Start : Kafka Consumer Start");
        startKafkaPipeline();
        this.isRunning = true;
    }

    @Override
    public void stop() {
        log.info("초기화 Stop: Kafka Consumer Stop");
        this.isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return this.isRunning;
    }
}
