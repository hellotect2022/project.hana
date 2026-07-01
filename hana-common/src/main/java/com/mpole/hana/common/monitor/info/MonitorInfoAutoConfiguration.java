package com.mpole.hana.common.monitor.info;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * hana-common 이 제공하는 모니터링용 InfoContributor 를 각 서비스에 자동 등록한다.
 * <p>
 * hana-common 패키지({@code com.mpole.hana.common})는 각 서비스의 컴포넌트 스캔 대상이
 * 아니므로, Spring Boot auto-configuration({@code AutoConfiguration.imports})으로 등록해야
 * 각 서비스 컨텍스트에 로드된다.
 * <p>
 * actuator 가 클래스패스에 있는 서비스에서만 활성화된다.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuate.info.InfoContributor")
public class MonitorInfoAutoConfiguration {

    @Bean
    public PidInfoContributor pidInfoContributor() {
        return new PidInfoContributor();
    }
}
