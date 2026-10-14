package com.mpole.hana.event.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Kafka 토픽 이름 — 외부 뷰 단위로 하나씩(gateway 의 동명 설정과 값이 일치해야 한다).
 * <p>토픽명이 어긋나면 발행/수신이 서로 다른 토픽을 보게 되어 데이터가 조용히 흐르지 않는다.</p>
 */
@ConfigurationProperties(prefix = "hdt.kafka.topic")
public record HdtKafkaTopicProperties(
        /** 뷰 #01 — 관제점 값(DeviceBundle). */
        @DefaultValue("view01") String view01,
        /** 뷰 #02 — 중계기 네트워크 상태(InfraStatus). */
        @DefaultValue("view02") String view02
) {
}
