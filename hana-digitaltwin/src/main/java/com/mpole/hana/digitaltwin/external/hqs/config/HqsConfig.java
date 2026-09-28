package com.mpole.hana.digitaltwin.external.hqs.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * HQS(현대 오토에버) 연동 설정 활성화.
 *
 * <p>{@code EmblazeIntegrationConfig} 를 회사 단위로 쪼갠 것이다 — emblaze 수신 쪽은
 * {@code external.emblaze.config.EmblazeConfig} 가 맡는다.</p>
 */
@Configuration
@EnableConfigurationProperties({HqsProperties.class})
public class HqsConfig {
}
