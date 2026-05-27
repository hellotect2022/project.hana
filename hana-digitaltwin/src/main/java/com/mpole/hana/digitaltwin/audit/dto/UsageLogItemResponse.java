package com.mpole.hana.digitaltwin.audit.dto;

import com.mpole.hana.digitaltwin.audit.entity.UsageLog;
import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record UsageLogItemResponse(
        Long usageLogId,
        OffsetDateTime createdAt,
        String path,
        String menuLabel,
        String loginId,
        String userAgent
) {
    public static UsageLogItemResponse from(UsageLog log) {
        return UsageLogItemResponse.builder()
                .usageLogId(log.getUsageLogId())
                .createdAt(log.getCreatedAt())
                .menuLabel(log.getMenuLabel())
                .loginId(log.getLoginId())
                .userAgent(log.getUserAgent())
                .build();
    }
}
