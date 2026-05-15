package com.mpole.hana.gateway.pubsub.kafka;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.gateway.config.properties.HdtKafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

@Component
@RequiredArgsConstructor
public class KafkaMessagePublisher {
    private final KafkaSender<String,Object> kafkaSender;
    private final HdtKafkaTopicProperties kafkaTopicProperties;


    public SenderRecord<String, Object,Long> createSenderRecord(DeviceBundle bundle) {
        return SenderRecord.create(
                new ProducerRecord<>(kafkaTopicProperties.raw(),
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



}
