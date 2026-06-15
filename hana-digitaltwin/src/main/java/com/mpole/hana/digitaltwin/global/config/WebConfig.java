package com.mpole.hana.digitaltwin.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@Configuration
@EnableSpringDataWebSupport(
        // Page 객체를 직렬화할 때, PageImpl 그대로가 아니라 안정적인 구조를 가진 DTO 형태로 변환하여 반환
        pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class WebConfig {
}
