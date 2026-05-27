package com.mpole.hana.digitaltwin.iam.auth.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record ActiveSessionsResponse(
        int totalCount,
        List<SessionInfo> sessions
) {
    @Builder
    public record SessionInfo(
            String deviceId,
            String deviceName,
            String deviceType,
            String ipAddress,
            OffsetDateTime lastUsedAt,
            OffsetDateTime expiresAt,
            boolean current
    ) {
    }
}
