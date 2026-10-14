package com.mpole.hana.digitaltwin.external.common;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 외부 연동용 {@link RestClient} 공용 팩토리.
 * <p>기존 XN 어댑터/토큰매니저가 각자 생성자에서 복붙하던
 * {@code request factory + timeout + RestClient.builder()} 로직을 공용화한 것이다.</p>
 *
 * <h3>왜 {@code SimpleClientHttpRequestFactory}(HttpURLConnection) 를 쓰지 않는가</h3>
 * <p>Spring 6.1 부터 요청 본문 버퍼링이 사라져 POST 가 항상 스트리밍 모드로 나가는데, JDK 의
 * {@code HttpURLConnection} 은 <b>스트리밍 모드에서 401 을 받으면 본문을 읽기 전에 연결을 끊는다</b>
 * ({@code HttpRetryException: cannot retry due to server authentication, in streaming mode}).
 * 그 결과 HQS 가 401 에 실어 보내는 {@code code/message}(IF241/IF243 등) 가 우리 쪽에서 빈 본문이 되어
 * 토큰 재발급 판정이 불가능했다(2026-09-30 운영 로그 {@code status=401 UNAUTHORIZED body=}).</p>
 *
 * <p>{@link ClientHttpRequestFactoryBuilder#jdk() JDK HttpClient} 는 이 문제가 없고 추가 의존성도 없다.
 * HTTP/1.1 로 고정하는 이유: 기본값(HTTP/2)은 평문 http 에도 {@code Upgrade: h2c} 를 보내는데,
 * 앞단 게이트웨이가 이를 잘못 다루는 경우가 있어 연동 대상이 확인되기 전까지는 보수적으로 둔다.</p>
 */
@Component
public class RestClientFactory {

    /** connect/read timeout 을 동일 값으로 적용해 RestClient 를 생성한다. */
    public RestClient create(int timeoutMs) {
        return create(timeoutMs, timeoutMs);
    }

    public RestClient create(int connectTimeoutMs, int readTimeoutMs) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(b -> b.version(HttpClient.Version.HTTP_1_1))
                .build(settings);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
