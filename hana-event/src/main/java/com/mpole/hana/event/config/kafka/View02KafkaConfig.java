package com.mpole.hana.event.config.kafka;

import com.mpole.hana.common.dto.InfraStatus;
import com.mpole.hana.event.config.properties.HdtKafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * <b>뷰 #02(중계기 네트워크 상태) Kafka 설정.</b>
 *
 * <p>뷰 #01 컨슈머와 <b>반드시 분리</b>해야 하는 이유:</p>
 * <ul>
 *   <li>역직렬화 타입이 yml 의 {@code spring.json.value.default.type}(=DeviceBundle)로 고정 →
 *       프로그램적으로 {@link InfraStatus} 로 오버라이드하지 않으면 조용히 오역직렬화된다.</li>
 *   <li>group.id 를 공유하면 서로 다른 구독을 가진 두 컨슈머가 같은 그룹에 들어가 리밸런스 시멘틱이 얽힌다.</li>
 *   <li><b>seekToEnd 를 쓰지 않는다</b> — 상태는 스냅샷이라 커밋 오프셋부터 읽어도 되고,
 *       매 사이클 전량 재발행되므로 재기동 시 다음 주기에 자연 수렴한다.</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hdt.dbview.view02", name = "enabled", havingValue = "true")
public class View02KafkaConfig {

    private static final String CONSUMER_GROUP = "hdt-event-view02-group";

    private final KafkaProperties kafkaProperties;
    private final HdtKafkaTopicProperties hdtKafkaTopicProperties;

    @Bean
    public KafkaReceiver<String, InfraStatus> view02KafkaReceiver() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        // 뷰 #01 컨슈머와 다른 타입/그룹으로 오버라이드
        props.put("spring.json.value.default.type", InfraStatus.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        ReceiverOptions<String, InfraStatus> receiverOptions =
                ReceiverOptions.<String, InfraStatus>create(props)
                        .subscription(Collections.singleton(hdtKafkaTopicProperties.view02()));

        return KafkaReceiver.create(receiverOptions);
    }
}
