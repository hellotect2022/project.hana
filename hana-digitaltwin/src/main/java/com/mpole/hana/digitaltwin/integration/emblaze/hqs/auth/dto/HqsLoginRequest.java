package com.mpole.hana.digitaltwin.integration.emblaze.hqs.auth.dto;

import com.mpole.hana.digitaltwin.iam.auth.dto.LoginRequest;
import jakarta.validation.constraints.NotBlank;

/**
 * HQS 로그인 요청 — {@code POST /api/v1/login/external}
 *
 * <p>HQS 명세의 필드명을 그대로 받는다. 내부적으로는
 * {@code clientId → loginId}, {@code clientValue → password} 로 매핑해
 * <b>기존 계정 체계를 그대로</b> 쓴다(전용 인증 로직을 따로 만들지 않는다).</p>
 *
 * <p><b>타입 주의</b> — 명세서 표의 데이터타입 칸은 {@code Long} 이지만 같은 문서의 요청 예시는
 * {@code "clientId": "sampleId"} 로 <b>문자열</b>이다. 우리 {@code login_id}/비밀번호가 문자열이라
 * String 으로 받는다. HQS 와 확인이 필요한 부분이다.</p>
 */
public record HqsLoginRequest(
        @NotBlank String clientId,
        @NotBlank String clientValue
) {

    /** 내부 로그인 요청으로 변환 — 인증은 {@code AuthService} 와 같은 규칙을 탄다. */
    public LoginRequest toLoginRequest() {
        return new LoginRequest(clientId, clientValue);
    }
}
