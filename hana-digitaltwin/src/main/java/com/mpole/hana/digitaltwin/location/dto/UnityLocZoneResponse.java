package com.mpole.hana.digitaltwin.location.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;

public record UnityLocZoneResponse(
        Long zoneId,
        String zoneName,
        String zoneMeshName
) {
    public static UnityLocZoneResponse from(UnityLocZone zone) {
        return new UnityLocZoneResponse(zone.getZoneId(), zone.getZoneName(), zone.getZoneMeshName());
    }
}
