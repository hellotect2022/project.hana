package com.mpole.hana.digitaltwin.space.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;
import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;
import com.mpole.hana.digitaltwin.space.entity.ZoneDisplayPolicy;

public record ZoneDisplayPolicyResponse(
        Long ruleId,
        Long zoneId,
        String zoneName,
        String buildingName,
        String floorName,
        String systemType,
        String systemTypeLabel,
        String displayName,
        String colorCode,
        Integer opacity,
        Boolean active,
        Boolean evacuationAutoShow
) {
    public static ZoneDisplayPolicyResponse from(ZoneDisplayPolicy p) {
        UnityLocZone zone = p.getZone();
        UnityLocFloor floor = zone != null ? zone.getFloor() : null;
        return new ZoneDisplayPolicyResponse(
                p.getRuleId(),
                zone != null ? zone.getZoneId() : null,
                zone != null ? zone.getZoneName() : null,
                floor != null && floor.getBuilding() != null ? floor.getBuilding().getBuildingName() : null,
                floor != null ? floor.getFloorName() : null,
                p.getSystemType().name(),
                p.getSystemType().getLabel(),
                p.getDisplayName(),
                p.getColorCode(),
                p.getOpacity(),
                p.getActive(),
                p.getEvacuationAutoShow()
        );
    }
}
