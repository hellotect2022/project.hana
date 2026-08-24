package com.mpole.hana.digitaltwin.integration.emblaze.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * emblaze/HQS 연동 설정 활성화.
 * <p>{@code @EnableScheduling} 은 {@code HanaDigitalTwinApplication} 에 이미 존재하므로 여기서 추가하지 않는다.</p>
 */
@Configuration
@EnableConfigurationProperties({HqsProperties.class, EmblazeProperties.class})
public class EmblazeIntegrationConfig {
}
