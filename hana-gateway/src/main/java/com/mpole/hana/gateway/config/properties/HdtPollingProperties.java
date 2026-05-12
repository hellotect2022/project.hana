package com.mpole.hana.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

import java.time.Duration;


@ConfigurationProperties(prefix="hdt.polling")
public record HdtPollingProperties(
        @DefaultValue("1000") long fixedDelayMs,
        @DefaultValue("5s") Duration pipelineBlockTimeout
) {
}
