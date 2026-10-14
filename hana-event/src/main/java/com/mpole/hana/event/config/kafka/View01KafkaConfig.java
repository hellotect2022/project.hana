package com.mpole.hana.event.config.kafka;

import com.mpole.hana.event.config.properties.HdtKafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.Collections;
import java.util.Map;

/**
 * <b>뷰 #01(관제점 값) Kafka 설정.</b>
 *
 * <p>gateway 가 뷰 #01 에서 수집·번들링한 {@code DeviceBundle} 을 받는 컨슈머와,
 * 공용 프로듀서(Sender)를 등록한다. 역직렬화 타입은 yml 의
 * {@code spring.json.value.default.type} 으로 DeviceBundle 에 고정돼 있다.</p>
 *
 * <p><b>seekToEnd</b> — 파티션 할당 때마다 최신 오프셋으로 이동한다. 값 스트림은
 * 다음 폴링(1초)에 최신값이 다시 오므로 다운타임 동안의 백로그를 재생하지 않는 편이
 * 밀린 데이터 폭주를 피할 수 있다. (뷰 #02 상태 스트림은 정책이 달라 별도 설정을 쓴다.)</p>
 */
@Configuration
@RequiredArgsConstructor
public class View01KafkaConfig {

    private final KafkaProperties kafkaProperties; // yml 설정값을 담고 있는 객체
    private final HdtKafkaTopicProperties hdtKafkaTopicProperties;

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
    public KafkaReceiver<String, Object> view01KafkaReceiver() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        ReceiverOptions<String, Object> receiverOptions = ReceiverOptions.<String, Object>create(props)
                .subscription(Collections.singleton(hdtKafkaTopicProperties.view01())) // 구독할 토픽 설정
                .addAssignListener(partitions -> {
                    // 컨슈머가 파티션을 할당받을 때마다 실행
                    partitions.forEach(p -> p.seekToEnd()); // 오프셋을 가장 최신 위치로 강제 이동
                });

        return KafkaReceiver.create(receiverOptions);
    }
}
