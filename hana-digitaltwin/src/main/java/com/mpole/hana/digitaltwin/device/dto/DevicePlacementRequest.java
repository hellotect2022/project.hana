package com.mpole.hana.digitaltwin.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DevicePlacementRequest(
        @NotNull Long deviceId,
        @NotBlank String deviceName,
        String description,
        Long unityZoneId,          // locBuildingId 등 4개 제거
        Float posX, Float posY, Float posZ,
        Float rotX, Float rotY, Float rotZ,
        Float scaleX, Float scaleY, Float scaleZ
) {
}
