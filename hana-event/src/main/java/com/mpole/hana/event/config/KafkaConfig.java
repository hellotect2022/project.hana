package com.mpole.hana.event.config;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.event.config.properties.HdtKafkaTopicProperties;
import com.sun.jdi.ObjectCollectedException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Sinks;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
//@EnableConfigurationProperties(AppKafkaProperties.class)
public class KafkaConfig {
    private final KafkaProperties kafkaProperties; // yml 설정값을 담고 있는 객체

    private final HdtKafkaTopicProperties hdtKafkaTopicProperties;

    @Bean
    public Sinks.Many<List<DeviceBundle>> centralSink() {
        // 카프카로부터 수신한 데이타를 참조하는 공유 메모리
        return Sinks.many().multicast().onBackpressureBuffer(20000);
    }

    @Bean
    public KafkaSender<String, Object> reactiveKafkaSender() {
        // 1. yml의 producer 설정들을 Map으로 가져옴
        Map<String, Object> props = kafkaProperties.buildProducerProperties();

        // 2. Reactor Kafka 전용 옵션 생성
        SenderOptions<String, Object> senderOptions = SenderOptions.create(props);

        // 3. Sender 생성 및 반환
        return KafkaSender.create(senderOptions);
    }


    @Bean
    public KafkaReceiver<String, Object> kafkaReceiver() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        ReceiverOptions receiverOptions = ReceiverOptions.create(props)
                .subscription(Collections.singleton(hdtKafkaTopicProperties.raw())) // 구독할 토픽 설정
                .addAssignListener(partitions -> {
                    // 컨슈머가 파티션을 할당받을 때마다 실행
                    partitions.forEach(p -> p.seekToEnd()); // 오프셋을 가장 최신 위치로 강제 이동
                });

        return KafkaReceiver.create(receiverOptions);
    }
}
