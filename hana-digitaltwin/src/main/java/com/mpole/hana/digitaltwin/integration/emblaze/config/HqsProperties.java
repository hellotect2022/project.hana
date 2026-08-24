package com.mpole.hana.digitaltwin.integration.emblaze.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HQS(3rd-party) 연동 설정. {@code hdt.integration.hqs.*} 로 덮어쓴다.
 * <p>XN({@code XnProperties}) 과 동일한 @Getter/@Setter + nested class 패턴(prefix 만 hqs).
 * 실제 설정(base-url 등)은 Loop4 승인 후 application.yml/env 로 주입하고,
 * 여기서는 코드 기본값만 둔다(yml 없이 컴파일·기동 가능).</p>
 */
@ConfigurationProperties(prefix = "hdt.integration.hqs")
@Getter
@Setter
public class HqsProperties {

    /** HQS 호출 대상 base URL (예: http://192.168.10.218:8000). 운영은 yml/env 주입. */
    private String baseUrl;

    private Paths paths = new Paths();

    /** HTTP connect/read timeout (ms) */
    private int timeoutMs = 3000;

    /**
     * 재실 → 퇴실 가정 전환 임계(ms). sweep 이 {@code now - lastMotionAt > 이 값} 인 재실 부스를 "0" 으로 전환한다.
     * <p>원본 하드코딩 10초(vacancy-assumtion-ms)는 화장실 재실 특성상 과도(움직임 없는 재실자 오탈락)하여
     * 기본 60초로 상향한다.</p>
     */
    private long vacancyTimeoutMs = 60000L;

    /** sweep 스케줄 주기(ms). {@code @Scheduled(fixedDelayString)} 에서도 동일 placeholder 로 참조. */
    private long sweepIntervalMs = 5000L;

    @Getter
    @Setter
    public static class Paths {
        /** 무선버튼(EnOcean) 이벤트 전송 경로 */
        private String wcButton = "/api/v1/wc/button";
    }
}
