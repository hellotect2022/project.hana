package com.mpole.hana.event.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "hdt.kafka.topic")
public record HdtKafkaTopicProperties(
        @DefaultValue("test") String raw
) {
}
