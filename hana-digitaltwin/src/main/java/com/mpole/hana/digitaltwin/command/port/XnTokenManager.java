package com.mpole.hana.digitaltwin.command.port;

import com.mpole.hana.digitaltwin.command.config.XnProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * XN 인증 토큰 관리자.
 * <p>{@code POST {base}/api/auth/token {id,key}} 로 토큰을 발급받아 메모리에 캐시하고,
 * 401/만료 시 재발급한다.</p>
 *
 * <p><b>가정(미확정):</b> 토큰 발급 응답 JSON 의 토큰 필드명이 확정되지 않아,
 * {@link XnProperties#getTokenResponseField()}(기본 "token") 를 우선 시도하고
 * 실패 시 {@code accessToken}/{@code access_token} 및 {@code data.*} 중첩까지 관용 파싱한다.
 * 실제 스펙 확인 후 {@code hdt.integration.xn.token-response-field} 로 확정 권장.</p>
 */
@Slf4j
@Component
public class XnTokenManager {

    private final XnProperties props;
    private final RestClient restClient;

    /** 캐시된 토큰 (volatile — 발급/무효화 가시성) */
    private volatile String cachedToken;
    /** 토큰 만료 시각(XN 응답 expiresAtUtc). null 이면 만료 정보 없음(401 시에만 재발급). */
    private volatile Instant expiresAt;
    /** 만료 전 재발급 여유(초). */
    private static final long EXPIRY_SKEW_SEC = 30;

    public XnTokenManager(XnProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getTimeoutMs());
        factory.setReadTimeout(props.getTimeoutMs());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /** 캐시된 토큰을 반환하고, 없으면 새로 발급한다. */
    public String getToken() {
        String token = cachedToken;
        if (token != null && !token.isBlank() && !isExpired()) {
            return token;
        }
        return refresh();
    }

    /** 만료 정보가 있고 (만료-여유) 이후면 만료로 간주. 만료 정보 없으면 만료 아님(401에 의존). */
    private boolean isExpired() {
        Instant exp = expiresAt;
        return exp != null && Instant.now().isAfter(exp.minusSeconds(EXPIRY_SKEW_SEC));
    }

    /** 강제 재발급 (401/만료 시 호출). */
    public synchronized String refresh() {
        String issued = requestToken();
        this.cachedToken = issued;
        return issued;
    }

    /** 캐시 무효화. 다음 호출 시 재발급된다. */
    public void invalidate() {
        this.cachedToken = null;
        this.expiresAt = null;
    }

    private String requestToken() {
        String url = props.getBaseUrl() + props.getEndpoints().getToken();
        Map<String, Object> body = Map.of(
                "id", props.getAuth().getId() != null ? props.getAuth().getId() : "",
                "key", props.getAuth().getKey() != null ? props.getAuth().getKey() : ""
        );
        Map<String, Object> response = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        String token = extractToken(response);
        if (token == null || token.isBlank()) {
            log.error("[XN-토큰] 발급 응답에서 토큰 필드를 찾지 못했습니다. 응답 키={}",
                    response != null ? response.keySet() : "(null)");
            throw new IllegalStateException("XN 토큰 발급 실패: 응답에서 토큰을 파싱할 수 없습니다.");
        }
        this.expiresAt = parseExpiry(response);
        log.info("[XN-토큰] 발급 성공 (만료: {})", expiresAt != null ? expiresAt : "정보없음");
        return token;
    }

    /** 응답의 expiresAtUtc(ISO-8601, 예 "2026-07-11T18:00:00Z") 를 Instant 로 파싱. 없거나 실패 시 null. */
    private Instant parseExpiry(Map<String, Object> response) {
        Object raw = response != null ? response.get("expiresAtUtc") : null;
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Instant.parse(s.trim());
            } catch (Exception e) {
                log.warn("[XN-토큰] expiresAtUtc 파싱 실패(만료정보 무시): {}", s);
            }
        }
        return null;
    }

    /**
     * 응답 Map 에서 토큰 문자열을 관용적으로 추출한다.
     * 설정 필드명 → token → accessToken → access_token 순으로 top-level 및 {@code data} 중첩을 탐색한다.
     */
    @SuppressWarnings("unchecked")
    private String extractToken(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Set<String> candidates = new LinkedHashSet<>();
        if (props.getTokenResponseField() != null && !props.getTokenResponseField().isBlank()) {
            candidates.add(props.getTokenResponseField());
        }
        candidates.add("token");
        candidates.add("accessToken");
        candidates.add("access_token");

        String found = firstStringValue(response, candidates);
        if (found != null) {
            return found;
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return firstStringValue((Map<String, Object>) dataMap, candidates);
        }
        return null;
    }

    private String firstStringValue(Map<String, Object> map, Set<String> keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
