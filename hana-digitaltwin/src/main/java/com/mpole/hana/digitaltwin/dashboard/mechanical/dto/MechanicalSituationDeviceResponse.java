package com.mpole.hana.digitaltwin.dashboard.mechanical.dto;

import lombok.Builder;

@Builder
public record MechanicalSituationDeviceResponse(
        Long deviceId,
        String deviceKey,
        String refDeviceCode,
        String status,
        String highlight,
        String updatedAt
) {
}
