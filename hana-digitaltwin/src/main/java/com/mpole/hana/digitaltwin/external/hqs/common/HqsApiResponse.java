package com.mpole.hana.digitaltwin.external.hqs.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 외부(HQS) 연동 공통 응답 래퍼.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HqsApiResponse<T> {

    private final String code;
    private final String message;
    private final OffsetDateTime timestamp;
    private final T data;

    private static final ZoneId HQS_ZONE = ZoneId.of("Asia/Seoul");

    public static <T> HqsApiResponse<T> success(T data) {
        return of(HqsResponseCode.SUCCESS, data);
    }

    public static <T> HqsApiResponse<T> success(String message, T data) {
        return HqsApiResponse.<T>builder()
                .code(HqsResponseCode.SUCCESS.getCode())
                .message(message)
                .timestamp(now())
                .data(data)
                .build();
    }

    public static <T> HqsApiResponse<T> of(HqsResponseCode responseCode, T data) {
        return HqsApiResponse.<T>builder()
                .code(responseCode.getCode())
                .message(responseCode.getMessage())
                .timestamp(now())
                .data(data)
                .build();
    }

    public static <T> HqsApiResponse<T> error(HqsResponseCode responseCode) {
        return HqsApiResponse.<T>builder()
                .code(responseCode.getCode())
                .message(responseCode.getMessage())
                .timestamp(now())
                .data(null)
                .build();
    }

    public static <T> HqsApiResponse<T> error(String code, String message) {
        return HqsApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(now())
                .data(null)
                .build();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(HQS_ZONE);
    }
}
