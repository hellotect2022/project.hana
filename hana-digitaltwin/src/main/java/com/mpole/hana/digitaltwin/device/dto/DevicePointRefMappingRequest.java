package com.mpole.hana.digitaltwin.device.dto;

import jakarta.validation.constraints.NotNull;

public record DevicePointRefMappingRequest(
        @NotNull Long pointId,
        String refDeviceCode,
        String refPointCode
) {
}
