package com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mpole.hana.digitaltwin.integration.emblaze.hqs.code.HqsResponseCode;

/**
 * <b>우리가 HQS 를 호출했을 때</b> 돌아오는 응답 봉투.
 *
 * <p>{@link HqsApiResponse} 와 헷갈리지 말 것 — 그쪽은 <b>HQS 가 우리를 호출했을 때</b>
 * 우리가 내려주는 응답이고 본문 필드가 {@code data} 다. 이쪽은 {@code result}(String) 다.</p>
 *
 * <p>HTTP 200 이어도 {@code code} 가 "0" 이 아니면 실패다 — 상태코드만 보고 성공으로 판단하면
 * 인증 만료·중복 같은 실패가 조용히 묻힌다.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HqsOutboundResponse(
        String code,
        String message,
        String timestamp,
        String result
) {

    /** 정상 처리 여부. {@code code} 가 없으면(응답 형식이 다르면) 실패로 본다. */
    public boolean isSuccess() {
        return HqsResponseCode.SUCCESS.getCode().equals(code);
    }

    /** 토큰이 만료·무효라 재로그인이 필요한 상태인가. */
    public boolean isAuthFailure() {
        return HqsResponseCode.AUTH_EXPIRE_TOKEN.getCode().equals(code)
                || HqsResponseCode.AUTH_INVALID_TOKEN.getCode().equals(code)
                || HqsResponseCode.AUTH_BLACKLIST_TOKEN.getCode().equals(code)
                || HqsResponseCode.AUTH_EXPIRED.getCode().equals(code);
    }
}
