package com.mpole.hana.digitaltwin.location.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocBuilding;
import lombok.Builder;

import java.util.List;

@Builder
public record UnityLocationResponse(
        Long buildingId,
        String buildingName,
        List<UnityLocFloorResponse> floorList
) {
    public static UnityLocationResponse from(UnityLocBuilding building) {
        return new UnityLocationResponse(
                building.getBuildingId(),
                building.getBuildingName(),
                building.getFloors().stream().map(UnityLocFloorResponse::from).toList()
        );
    }
}
