package com.mpole.hana.digitaltwin.location.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocBuilding;

public record UnityLocBuildingResponse(
        Long buildingId,
        String buildingName
) {
    public static UnityLocBuildingResponse from(UnityLocBuilding b) {
        return new UnityLocBuildingResponse(b.getBuildingId(), b.getBuildingName());
    }
}
