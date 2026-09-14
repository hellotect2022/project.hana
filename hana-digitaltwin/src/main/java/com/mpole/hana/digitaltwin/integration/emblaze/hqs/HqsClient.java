package com.mpole.hana.digitaltwin.integration.emblaze.hqs;

import com.mpole.hana.digitaltwin.integration.emblaze.config.HqsProperties;
import com.mpole.hana.digitaltwin.integration.emblaze.config.RestClientFactory;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsOutboundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

/**
 * HQS 외부 호출 클라이언트.
 *
 * <p>원본은 WebFlux {@code WebClient} 였으나 digitaltwin(WebMVC) 정합을 위해
 * Spring {@link RestClient}(동기)로 교체했다.</p>
 *
 * <h3>공통 헤더</h3>
 * <pre>
 *   Authorization: Bearer &lt;access token&gt;   login 으로 발급받은 토큰
 *   X-trxId:       &lt;uuid&gt;                   호출 1건마다 새로 만드는 추적 ID
 * </pre>
 *
 * <h3>실패 판정</h3>
 * <p>HTTP 200 이어도 본문 {@code code} 가 {@code "0"} 이 아니면 실패다 — 상태코드만 보면
 * 인증 만료·중복 같은 실패가 조용히 성공으로 묻힌다. 토큰 만료(241/243 등)면
 * {@link HqsTokenProvider#refresh()} 후 <b>단 1회</b> 재시도한다(무한 재시도 방지).</p>
 */
@Slf4j
@Component
public class HqsClient {

    /** 트랜잭션 추적 헤더명(명세). */
    public static final String HEADER_TRX_ID = "X-trxId";

    private final HqsProperties props;
    private final HqsTokenProvider tokenProvider;
    private final RestClient restClient;

    public HqsClient(HqsProperties props, HqsTokenProvider tokenProvider,
                     RestClientFactory restClientFactory) {
        this.props = props;
        this.tokenProvider = tokenProvider;
        this.restClient = restClientFactory.create(props.getTimeoutMs());
    }

    /**
     * 무선버튼(EnOcean) 이벤트를 HQS 로 전송한다. {@code POST {base-url}{paths.wcButton}}
     *
     * @return HQS 응답. 호출 자체가 실패하면 null — 버튼 누름은 재전송 큐가 없으므로
     *         호출부는 로그만 남기고 누적 카운트는 계속 진행한다.
     */
    public HqsOutboundResponse postWcButton(Object body) {
        String url = props.getBaseUrl() + props.getPaths().getWcButton();
        String trxId = newTrxId();

        HqsOutboundResponse response = exchange(url, body, trxId, false);
        if (response != null && response.isAuthFailure() && tokenProvider.refresh()) {
            log.info("HQS 토큰 재발급 후 재시도 trxId={}", trxId);
            response = exchange(url, body, newTrxId(), true);
        }
        return response;
    }

    /**
     * 실제 전송 1회.
     *
     * @param isRetry 재시도 호출인지 — 로그 구분용
     */
    private HqsOutboundResponse exchange(String url, Object body, String trxId, boolean isRetry) {
        // 토큰이 없으면 여기서 로그인이 일어난다(HqsTokenProvider.currentToken).
        String token = tokenProvider.currentToken();
        if (!StringUtils.hasText(token)) {
            log.warn("HQS 토큰 확보 실패(로그인 실패) — Authorization 없이 호출한다. 인증 오류가 예상된다");
        }

        try {
            HqsOutboundResponse response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        headers.set(HEADER_TRX_ID, trxId);
                        if (StringUtils.hasText(token)) {
                            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(HqsOutboundResponse.class);

            if (response == null) {
                log.error("HQS 응답 본문 없음 url={} trxId={}", url, trxId);
            } else if (!response.isSuccess()) {
                log.error("HQS 응답 실패 url={} trxId={} code={} message={}",
                        url, trxId, response.code(), response.message());
            } else {
                log.info("HQS 전송 성공{} trxId={} result={}", isRetry ? "(재시도)" : "", trxId, response.result());
            }
            return response;

        } catch (RestClientResponseException e) {
            // 4xx/5xx — 본문에 code/message 가 실려 올 수 있어 그대로 남긴다
            log.error("HQS 호출 실패 url={} trxId={} status={} body={}",
                    url, trxId, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("HQS 호출 오류 url={} trxId={}: {}", url, trxId, e.getMessage());
            return null;
        }
    }

    /**
     * 호출 1건마다 새로 만드는 추적 ID.
     * <p>명세 비고가 {@code uuid-uuid....} 라 형식이 모호하다 — 표준 UUID(하이픈 포함)로 보낸다.
     * HQS 가 다른 형식을 요구하면 여기만 고치면 된다.</p>
     */
    private static String newTrxId() {
        return UUID.randomUUID().toString();
    }
}
