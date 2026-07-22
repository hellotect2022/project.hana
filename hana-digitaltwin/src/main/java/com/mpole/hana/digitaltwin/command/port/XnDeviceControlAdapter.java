package com.mpole.hana.digitaltwin.command.port;

import com.mpole.hana.digitaltwin.command.config.XnProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * XN 제어 API 어댑터. {@code POST {base}/api/device/command} 로
 * {@code [{deviceCode, pointCode, value}]} 페이로드를 전송한다.
 * <p>Spring 6 {@link RestClient}(spring-web 내장) 사용 — 신규 의존성 없음.
 * 401(만료) 발생 시 토큰을 재발급해 1회 재시도한다.</p>
 */
@Slf4j
@Component
public class XnDeviceControlAdapter implements DeviceControlPort {

    private final XnProperties props;
    private final XnTokenManager tokenManager;
    private final RestClient restClient;

    public XnDeviceControlAdapter(XnProperties props, XnTokenManager tokenManager) {
        this.props = props;
        this.tokenManager = tokenManager;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getTimeoutMs());
        factory.setReadTimeout(props.getTimeoutMs());
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public boolean dispatch(String refDeviceCode, String refPointCode, String value) {
        if (refDeviceCode == null || refPointCode == null) {
            log.warn("[XN-제어] ref 코드가 비어 있어 전송할 수 없습니다. deviceCode={}, pointCode={}",
                    refDeviceCode, refPointCode);
            return false;
        }
        List<Map<String, String>> payload = List.of(Map.of(
                "deviceCode", refDeviceCode,
                "pointCode", refPointCode,
                "value", value
        ));
        try {
            return send(payload, false);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                log.info("[XN-제어] 401 — 토큰 재발급 후 재시도");
                tokenManager.invalidate();
                try {
                    return send(payload, true);
                } catch (Exception retry) {
                    log.error("[XN-제어] 재시도 실패: {}", retry.getMessage());
                    return false;
                }
            }
            log.error("[XN-제어] 전송 실패 status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("[XN-제어] 전송 실패: {}", e.getMessage());
            return false;
        }
    }

    private boolean send(List<Map<String, String>> payload, boolean forceRefresh) {
        String token = forceRefresh ? tokenManager.refresh() : tokenManager.getToken();
        String url = props.getBaseUrl() + props.getEndpoints().getCommand();
        HttpStatusCode status = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(payload)
                .retrieve()
                .toBodilessEntity()
                .getStatusCode();
        boolean accepted = status.is2xxSuccessful();
        log.info("[XN-제어] 전송 완료 status={}, 대상={}건", status, payload.size());
        return accepted;
    }
}
