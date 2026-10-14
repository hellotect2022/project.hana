package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

import java.util.Map;

@Builder
public record PowerMccRuntimeItemResponse(
        String hotspotId,
        Long deviceId,
        String deviceKey,
        String deviceName,
        boolean linked,
        boolean commOk,
        String updatedAt,
        Map<String, String> valuesByTag
) {
}
