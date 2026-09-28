package com.mpole.hana.digitaltwin.external.emblaze.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * emblaze 수신 연동 설정 활성화.
 *
 * <p>{@code EmblazeIntegrationConfig} 를 회사 단위로 쪼갠 것이다 — 한 클래스가 emblaze(수신)와
 * HQS(제공·푸시) 양쪽 properties 를 함께 켜고 있어서, 패키지를 갈라도 설정만 붙어 있었다.
 * HQS 쪽은 {@code external.hqs.config.HqsConfig} 가 맡는다.</p>
 *
 * <p>{@code @EnableScheduling} 은 {@code HanaDigitalTwinApplication} 에 이미 존재하므로 여기서 추가하지 않는다.</p>
 */
@Configuration
@EnableConfigurationProperties({EmblazeProperties.class})
public class EmblazeConfig {
}
