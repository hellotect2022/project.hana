package com.mpole.hana.digitaltwin.integration.emblaze.hqs;

import com.mpole.hana.digitaltwin.integration.emblaze.config.HqsProperties;
import com.mpole.hana.digitaltwin.integration.emblaze.config.RestClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HQS 외부 호출 클라이언트.
 * <p>원본은 WebFlux {@code WebClient} 였으나, digitaltwin(WebMVC) 정합을 위해 Spring {@link RestClient}(동기)로 교체했다.
 * 공용 {@link RestClientFactory} 로 timeout 적용된 client 를 생성한다. 인증 없음(원본대로).</p>
 */
@Slf4j
@Component
public class HqsClient {

    private final HqsProperties props;
    private final RestClient restClient;

    public HqsClient(HqsProperties props, RestClientFactory restClientFactory) {
        this.props = props;
        this.restClient = restClientFactory.create(props.getTimeoutMs());
    }

    /**
     * 무선버튼(EnOcean) 이벤트를 HQS 로 전송한다. {@code POST {base-url}{paths.wcButton}} (동기, 응답 String).
     */
    public String postWcButton(Object body) {
        String url = props.getBaseUrl() + props.getPaths().getWcButton();
        return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }
}
