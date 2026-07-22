package com.mpole.hana.digitaltwin.iam.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 액세스 토큰 재발급 요청. 리프레시 토큰을 body 로 전달받는다.
 * (Bearer 헤더는 access 토큰 용도이므로 refresh 토큰은 body 로 받는다 — OAuth2 관례)
 */
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
}
