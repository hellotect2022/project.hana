package com.mpole.hana.digitaltwin.command.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 연계사(XN) 제어 API 설정. {@code hdt.integration.xn.*} 로 덮어쓴다.
 * <p>인증키(auth.key)는 시크릿이므로 application.yml 에는 env 로만 주입한다.</p>
 */
@ConfigurationProperties(prefix = "hdt.integration.xn")
@Getter
@Setter
public class XnProperties {

    /** XN API base URL (예: http://210.91.59.66:6500) */
    private String baseUrl;

    private Auth auth = new Auth();

    private Endpoints endpoints = new Endpoints();

    /** HTTP connect/read timeout (ms) */
    private int timeoutMs = 3000;

    /**
     * 토큰 발급 응답 JSON 에서 액세스 토큰을 담은 필드명.
     * <p>XN 응답 스펙 미확정 — 기본 "token" 이며 실패 시 accessToken/access_token 도 관용 파싱한다.</p>
     */
    private String tokenResponseField = "token";

    @Getter
    @Setter
    public static class Auth {
        /** 토큰 발급 body 의 id */
        private String id;
        /** 토큰 발급 body 의 key (시크릿) */
        private String key;
    }

    @Getter
    @Setter
    public static class Endpoints {
        /** 토큰 발급 경로 */
        private String token = "/api/auth/token";
        /** 제어 명령 경로 */
        private String command = "/api/device/command";
    }
}
