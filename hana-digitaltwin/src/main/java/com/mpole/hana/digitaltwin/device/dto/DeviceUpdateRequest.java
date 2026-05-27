package com.mpole.hana.digitaltwin.device.dto;

import java.util.List;

public record DeviceUpdateRequest(
        String deviceName,
        String deviceKey,
        String description,
        Boolean active,
        Long categoryId,
        Long assetId,
        List<DevicePointRegisterItem> points
) {
}
