package com.mpole.hana.digitaltwin.integration.emblaze.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 외부 연동용 {@link RestClient} 공용 팩토리.
 * <p>기존 XN 어댑터/토큰매니저가 각자 생성자에서 복붙하던
 * {@code SimpleClientHttpRequestFactory + timeout + RestClient.builder()} 로직을 공용화한 것이다.
 * HQS 는 현재 인증이 없어 이 팩토리만으로 충분하며, 향후 인증이 생기면 XN 패턴을 승격하면 된다.</p>
 */
@Component
public class RestClientFactory {

    /** connect/read timeout 을 동일 값으로 적용해 RestClient 를 생성한다. */
    public RestClient create(int timeoutMs) {
        return create(timeoutMs, timeoutMs);
    }

    public RestClient create(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
