package com.mpole.hana.digitaltwin.audit.dto;

import com.mpole.hana.digitaltwin.audit.entity.AccessLog;
import com.mpole.hana.digitaltwin.audit.entity.AccessLogType;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record AccessLogItemResponse(
        Long accessLogId,
        OffsetDateTime createdAt,
        String type,
        String loginId,
        String username,
        String userAgent,
        String clientIp,
        String detail
) {
    public static AccessLogItemResponse from(AccessLog log) {
        return AccessLogItemResponse.builder()
                .accessLogId(log.getAccessLogId())
                .createdAt(log.getCreatedAt())
                .type(mapType(log.getLogType()))
                .loginId(log.getLoginId())
                .username(log.getUsername())
                .userAgent(log.getUserAgent())
                .clientIp(log.getClientIp())
                .detail(log.getDetail())
                .build();
    }

    private static String mapType(AccessLogType type) {
        return type != null ? type.name() : "";
    }
}
