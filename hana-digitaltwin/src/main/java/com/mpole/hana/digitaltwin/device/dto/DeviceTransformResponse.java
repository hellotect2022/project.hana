package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.entity.DevicePlacement;

public record DeviceTransformResponse(
        Long placementId,
        Float posX, Float posY, Float posZ,
        Float rotX, Float rotY, Float rotZ,
        Float scaleX, Float scaleY, Float scaleZ
) {
    public static DeviceTransformResponse from(DevicePlacement p) {
        if (p == null) return null;
        return new DeviceTransformResponse(
                p.getPlacementId(),
                p.getPosX(), p.getPosY(), p.getPosZ(),
                p.getRotX(), p.getRotY(), p.getRotZ(),
                p.getScaleX(), p.getScaleY(), p.getScaleZ()
        );
    }
}
