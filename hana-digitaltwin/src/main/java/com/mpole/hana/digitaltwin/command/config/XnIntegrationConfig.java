package com.mpole.hana.digitaltwin.command.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 외부 연계사(XN) 제어 연동 설정 활성화.
 */
@Configuration
@EnableConfigurationProperties(XnProperties.class)
public class XnIntegrationConfig {
}
