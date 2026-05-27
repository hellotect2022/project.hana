package com.mpole.hana.digitaltwin.location.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;

import java.util.List;

public record UnityLocFloorResponse(
        Long floorId,
        Integer floorNum,
        String floorName,
        List<UnityLocZoneResponse> zoneList
) {
    public static UnityLocFloorResponse from(UnityLocFloor floor) {
        return new UnityLocFloorResponse(
                floor.getFloorId(),
                floor.getFloorNum(),
                floor.getFloorName(),
                floor.getZones().stream().map(UnityLocZoneResponse::from).toList()
        );
    }
}
