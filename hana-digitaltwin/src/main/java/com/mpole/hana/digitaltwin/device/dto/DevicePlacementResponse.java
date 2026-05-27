package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePlacement;

public record DevicePlacementResponse(
        Long deviceId,
        String deviceName,
        String description,
        DeviceTransformResponse transform,
        DeviceLocationResponse location,
        Boolean active,
        boolean isSet
) {
    public static DevicePlacementResponse from(Device device) {
        DevicePlacement p = device.getPlacement();
        return new DevicePlacementResponse(
                device.getDeviceId(),
                device.getDeviceName(),
                device.getDescription(),
                DeviceTransformResponse.from(p),
                DeviceLocationResponse.from(p),
                device.getActive(),
                p != null
        );
    }
}
