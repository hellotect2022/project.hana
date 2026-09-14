package com.mpole.hana.digitaltwin.integration.emblaze.hqs;

import com.mpole.hana.digitaltwin.integration.emblaze.config.HqsProperties;
import com.mpole.hana.digitaltwin.integration.emblaze.config.RestClientFactory;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto.HqsOutboundResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HQS 호출용 access token 공급 — 로그인해서 받아오고 캐시한다.
 *
 * <pre>
 *   POST {base-url}/api/v1/login/external
 *   X-trxId: &lt;uuid&gt;
 *   { "clientId": "...", "clientValue": "..." }
 *     → { "code":"0", "data":"&lt;JWT&gt;", ... }
 * </pre>
 *
 * <h3>왜 {@link HqsClient} 를 쓰지 않고 자체 RestClient 를 드는가</h3>
 * <p>{@code HqsClient} 는 모든 호출에 이 클래스의 토큰을 싣는다. 그래서 여기서 {@code HqsClient} 를
 * 주입받으면 <b>{@code HqsClient} ↔ {@code HqsTokenProvider} 순환 참조</b>가 되어 기동이 실패한다.
 * 로그인은 애초에 Authorization 이 붙으면 안 되는 호출이라(토큰을 받으러 가는 요청),
 * 의존을 한 방향으로 두고 여기서 직접 보낸다.</p>
 *
 * <h3>갱신 시점</h3>
 * <p>만료 시각을 알 수 없다 — HQS 응답에 유효기간이 없다. 그래서 <b>미리 갱신하지 않고</b>
 * 호출이 인증 실패(241/243 등)로 돌아왔을 때 {@link #refresh()} 로 다시 받는다.
 * {@code HqsClient} 가 그 경우 1회만 재시도한다.</p>
 *
 * <h3>동시성</h3>
 * <p>토큰 만료 시점에 버튼이 여러 개 눌리면 스레드 여러 개가 동시에 갱신하러 온다.
 * 로그인을 {@code synchronized} 로 묶고 <b>이중 검사</b>를 둬서, 내가 락을 기다리는 사이 다른 스레드가
 * 이미 새 토큰을 받아놨으면 그것을 쓰고 로그인을 건너뛴다.</p>
 */
@Component
@Slf4j
public class HqsTokenProvider {

    private final HqsProperties props;
    private final RestClient restClient;

    /** 현재 토큰. 최초 호출 시 로그인으로 채워지고, 인증 실패 시 교체된다. */
    private final AtomicReference<String> cached = new AtomicReference<>();

    public HqsTokenProvider(HqsProperties props, RestClientFactory restClientFactory) {
        this.props = props;
        this.restClient = restClientFactory.create(props.getTimeoutMs());
    }

    /**
     * 지금 쓸 토큰. 없으면 로그인해서 받아온다.
     *
     * <p>{@code hdt.integration.hqs.access-token} 이 설정돼 있으면 그것을 우선 쓴다 —
     * 로그인이 막혔을 때 사람이 받은 토큰을 직접 꽂아 넣는 비상 경로다.</p>
     *
     * @return 토큰. 로그인까지 실패하면 null — 호출부는 Authorization 없이 보내고 경고를 남긴다.
     */
    public String currentToken() {
        String token = cached.get();
        if (StringUtils.hasText(token)) {
            return token;
        }

        String manual = props.getAccessToken();
        if (StringUtils.hasText(manual)) {
            log.info("HQS 토큰: 설정값(access-token) 사용 — 로그인 생략");
            cached.compareAndSet(null, manual.trim());
            return cached.get();
        }

        return login(null);
    }

    /**
     * 토큰 재발급. 호출이 인증 실패로 돌아왔을 때 {@link HqsClient} 가 부른다.
     *
     * @return 새 토큰을 확보했으면 true — 호출부가 그때만 재시도한다
     */
    public boolean refresh() {
        String stale = cached.get();
        return StringUtils.hasText(login(stale));
    }

    /** 설정을 바꿔 넣었을 때 캐시를 비운다(운영 중 토큰 교체용). */
    public void invalidate() {
        cached.set(null);
    }

    /**
     * 로그인 1회. 같은 순간에 여러 스레드가 들어와도 <b>한 번만</b> 나간다.
     *
     * @param staleToken 실패한 토큰. 내가 락을 기다리는 사이 다른 스레드가 이미 갈아치웠는지 판단하는 기준.
     *                   최초 발급이면 null.
     */
    private synchronized String login(String staleToken) {
        String current = cached.get();
        if (StringUtils.hasText(current) && !current.equals(staleToken)) {
            // 다른 스레드가 이미 새로 받아왔다 — 중복 로그인하지 않는다.
            return current;
        }

        String clientId = props.getLogin().getClientId();
        String clientValue = props.getLogin().getClientValue();
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientValue)) {
            log.error("HQS 로그인 자격증명 미설정 — hdt.integration.hqs.login.client-id / client-value 확인 필요");
            return null;
        }

        String url = props.getBaseUrl() + props.getPaths().getLogin();
        String trxId = UUID.randomUUID().toString();

        try {
            HqsOutboundResponse response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HqsClient.HEADER_TRX_ID, trxId)   // 로그인에는 Authorization 을 붙이지 않는다
                    .body(Map.of("clientId", clientId, "clientValue", clientValue))
                    .retrieve()
                    .body(HqsOutboundResponse.class);

            if (response == null) {
                log.error("HQS 로그인 응답 본문 없음 url={} trxId={}", url, trxId);
                return null;
            }
            if (!response.isSuccess()) {
                log.error("HQS 로그인 실패 url={} trxId={} code={} message={}",
                        url, trxId, response.code(), response.message());
                return null;
            }
            // 로그인 응답의 JWT 는 result 가 아니라 data 에 담긴다.
            String token = response.data();
            if (!StringUtils.hasText(token)) {
                log.error("HQS 로그인 성공했으나 data(JWT)가 비어 있음 trxId={}", trxId);
                return null;
            }

            cached.set(token.trim());
            log.info("HQS 로그인 성공 — 토큰 발급 trxId={}", trxId);
            return cached.get();

        } catch (RestClientResponseException e) {
            log.error("HQS 로그인 호출 실패 url={} trxId={} status={} body={}",
                    url, trxId, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("HQS 로그인 오류 url={} trxId={}: {}", url, trxId, e.getMessage());
            return null;
        }
    }
}
