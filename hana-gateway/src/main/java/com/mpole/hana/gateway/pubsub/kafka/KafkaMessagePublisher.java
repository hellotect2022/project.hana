package com.mpole.hana.gateway.pubsub.kafka;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.InfraStatus;
import com.mpole.hana.gateway.config.properties.HdtKafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KafkaMessagePublisher {
    private final KafkaSender<String,Object> kafkaSender;
    private final HdtKafkaTopicProperties kafkaTopicProperties;


    public SenderRecord<String, Object,Long> createSenderRecord(DeviceBundle bundle) {
        return SenderRecord.create(
                new ProducerRecord<>(kafkaTopicProperties.view01(),
                        bundle.deviceId().toString(),
                        bundle
                ),
                bundle.deviceId()
        );
    }

    public Mono<Void> publish(DeviceBundle bundle) {
        return kafkaSender.send(
                Mono.just(createSenderRecord(bundle))
        ).then();

    }

    public Flux<SenderResult<Long>> publishAll(Flux<DeviceBundle> bundleFlux) {
        return kafkaSender.send(
                bundleFlux.map(this::createSenderRecord)
        );
    }

    /**
     * 인프라 상태 스냅샷 발행 — 전용 토픽, key=ref_infra_code(같은 인프라는 같은 파티션 → 순서 보장).
     * 값 토픽과 스키마가 달라 컨슈머도 분리된다.
     */
    public Mono<Void> publishAllInfraStatus(List<InfraStatus> statuses) {
        return kafkaSender.send(
                Flux.fromIterable(statuses).map(s -> SenderRecord.create(
                        new ProducerRecord<String, Object>(
                                kafkaTopicProperties.view02(),
                                s.refInfraCode(),
                                s),
                        0L))
        ).then();
    }



}
