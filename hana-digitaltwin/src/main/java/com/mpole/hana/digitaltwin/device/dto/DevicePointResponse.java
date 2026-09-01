package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.entity.DevicePoint;

public record DevicePointResponse(
        Long pointId,
        String pointKey,
        String tagName,
        String description,
        String pointDisplayName,
        String pointType,
        String unit,
        Boolean active,
        String refDeviceCode,
        String refPointCode
) {
    public static DevicePointResponse from(DevicePoint point) {
        return new DevicePointResponse(
                point.getPointId(),
                point.getPointKey(),
                point.getTagName(),
                point.getDescription(),
                point.getPointDisplayName(),
                point.getPointType(),
                point.getUnit(),
                point.getActive(),
                point.getRefDeviceCode(),
                point.getRefPointCode()
        );
    }
}
