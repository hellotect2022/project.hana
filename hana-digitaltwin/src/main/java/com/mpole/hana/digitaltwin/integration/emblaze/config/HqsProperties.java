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
     * 재실 → 퇴실 판정 임계(ms). <b>마지막 움직임 이후</b> 이만큼 지난 부스는 조회에서 0(비어있음)으로 읽는다.
     *
     * <p>예전에는 스케줄러가 이 값으로 DB 에 "0" 을 써 넣었지만, 지금은 조회 쿼리가
     * {@code now - occ.updateDatetime} 과 비교해 <b>계산</b>한다 — 스케줄러도 메모리 상태도 없다.</p>
     *
     * <p>원본 하드코딩 10초(vacancy-assumtion-ms)는 화장실 재실 특성상 과도(움직임 없는 재실자 오탈락)하여
     * 기본 60초로 상향한다. 센서가 죽어 메시지가 끊긴 경우도 같은 검사에 걸린다.</p>
     *
     * <p>{@code 0} 이하면 시간 검사를 하지 않는다(마지막 기록값을 그대로 노출).</p>
     */
    private long vacancyTimeoutMs = 60000L;

    // [보존] 퇴실 sweep 스케줄 주기 — 조회 시점 계산으로 바뀌어 스케줄러가 사라졌다.
    // private long sweepIntervalMs = 5000L;

    /**
     * HQS 로 보낼 건물 식별자({@code wcButton} 페이로드의 {@code bldgId}).
     * <p>대상 건물이 하나뿐이라 상수로 둔다. 비어 있으면 전송 시 경고만 남기고 빈 값으로 보낸다.</p>
     */
    private String buildingId = "1";

    /**
     * 물리버튼 누적 카운트를 기록할 통계 장비의 {@code device_key}.
     * <p>비어 있거나 해당 장비가 없으면 카운트를 건너뛴다(HQS 전송은 계속된다).</p>
     */
    private String statsDeviceKey = "";

    /**
     * HQS 호출용 access token ({@code Authorization: Bearer <token>}).
     *
     * <p>명세상 login 으로 발급받는 값인데 <b>login API 명세를 아직 받지 못해</b> 자동 발급을
     * 구현할 수 없다. 그때까지는 발급받은 토큰을 여기(또는 환경변수)에 넣어 쓴다.
     * 비어 있으면 Authorization 없이 호출하고 경고를 남긴다.</p>
     *
     * <p><b>토큰은 자격증명이다</b> — yml 에 평문으로 커밋하지 말고 환경변수로 주입할 것.</p>
     */
    private String accessToken = "";

    @Getter
    @Setter
    public static class Paths {
        /** 무선버튼(EnOcean) 이벤트 전송 경로 */
        private String wcButton = "/api/v1/wc/button";
    }
}
