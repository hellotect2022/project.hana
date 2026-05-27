package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.entity.DevicePlacement;
import com.mpole.hana.digitaltwin.location.entity.UnityLocBuilding;
import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;
import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;

public record DeviceLocationResponse(
        Long buildingId, String buildingName,
        Long floorId, String floorName,
        Long zoneId, String zoneName, String zoneMeshName
) {
    public static DeviceLocationResponse from(DevicePlacement p) {
        if (p == null || p.getUnityZone() == null) return empty();
        UnityLocZone z = p.getUnityZone();
        UnityLocFloor f = z.getFloor();
        UnityLocBuilding b = f.getBuilding();
        return new DeviceLocationResponse(
                b.getBuildingId(), b.getBuildingName(),
                f.getFloorId(), f.getFloorName(),
                z.getZoneId(), z.getZoneName(), z.getZoneMeshName()
        );
    }
    private static DeviceLocationResponse empty() {
        return new DeviceLocationResponse(null, null, null, null, null, null, null);
    }
}
