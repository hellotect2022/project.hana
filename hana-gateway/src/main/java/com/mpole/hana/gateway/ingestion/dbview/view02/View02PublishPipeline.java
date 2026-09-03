package com.mpole.hana.gateway.ingestion.dbview.view02;

import com.mpole.hana.gateway.pubsub.kafka.KafkaMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * <b>외부 뷰 #02 파이프라인 1사이클</b> — 중계기 상태 전량 읽기 → Kafka(전용 토픽) 발행.
 *
 * <p>뷰 #01 과 달리 변경감지·번들링 단계가 없다. 상태 스냅샷을 그대로 흘려보내고,
 * "무엇이 바뀌었는지"의 판정은 소비자(event)가 DB 와 비교해 수행한다(멱등 수렴).</p>
 *
 * <p>값(DeviceBundle) 토픽과는 <b>스키마가 다르므로 반드시 별도 토픽</b>으로 발행한다
 * — 같은 토픽에 실으면 소비자의 고정 역직렬화 타입 때문에 오역직렬화된다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class View02PublishPipeline {

    private final View02ReadService readService;
    private final KafkaMessagePublisher kafkaPublisher;

    public Mono<Void> runOneCycle() {
        long start = System.currentTimeMillis();
        return readService.readAll()
                .subscribeOn(Schedulers.boundedElastic())
                .collectList()
                .flatMap(list -> {
                    if (list.isEmpty()) {
                        log.warn("[View02] 조회 결과 0건 — 뷰/컬럼 설정 확인 필요");
                        return Mono.<Void>empty();
                    }

                    //log.info("[View02] {}건 발행 완료 ({}ms)", list.size(), System.currentTimeMillis() - start);
                    //return Mono.just(list);
                    return kafkaPublisher.publishAllInfraStatus(list)
                            .doOnSuccess(v -> log.info("[View02] {}건 발행 완료 ({}ms)", list.size(), System.currentTimeMillis() - start));
                })
                .then();
    }

    /** 스케줄러에서 호출 — 사이클 실패가 스케줄을 죽이지 않도록 로깅 후 흡수한다(다음 주기 재시도). */
    public Mono<Void> runOneCycleSafely() {
        return runOneCycle()
                .doOnError(e -> log.error("[View02] 사이클 실패(다음 주기 재시도): {}", e.getMessage(),e))
                .onErrorResume(e -> Mono.empty());
    }
}
