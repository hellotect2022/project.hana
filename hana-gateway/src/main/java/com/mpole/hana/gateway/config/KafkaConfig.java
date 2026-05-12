package com.mpole.hana.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {
    private final KafkaProperties kafkaProperties; // yml 설정값을 담고 있는 객체

    @Bean
    public KafkaSender<String, Object> reactiveKafkaSender() {
        // 1. yml의 producer 설정들을 Map으로 가져옴
        Map<String, Object> props = kafkaProperties.buildProducerProperties();

        // 2. Reactor Kafka 전용 옵션 생성
        SenderOptions<String, Object> senderOptions = SenderOptions.create(props);

        // 3. Sender 생성 및 반환
        return KafkaSender.create(senderOptions);
    }
}
