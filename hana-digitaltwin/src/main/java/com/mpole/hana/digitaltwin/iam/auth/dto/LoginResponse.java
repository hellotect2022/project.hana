package com.mpole.hana.digitaltwin.iam.auth.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserInfo userInfo,
        boolean passwordChangeRequired,
        Integer daysUntilPasswordExpiry,
        String currentDeviceId,
        List<ActiveDevice> activeDevices
) {
    @Builder
    public record UserInfo(
            String loginId,
            String email,
            String name,
            List<String> roles
    ) { }

    @Builder
    public record ActiveDevice(
            String deviceId,
            String deviceName,
            String deviceType,
            String ipAddress,
            String lastUsedAt,
            boolean current
    ) {
    }
}
