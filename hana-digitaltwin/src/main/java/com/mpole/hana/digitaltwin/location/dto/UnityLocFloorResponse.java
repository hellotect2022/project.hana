package com.mpole.hana.digitaltwin.location.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;

import java.util.List;

public record UnityLocFloorResponse(
        Long floorId,
        Integer floorNum,
        /** 표시 정렬 순서. 0 = 최하층(B7F) → 24 = PH2F. 배열은 이미 이 순서로 정렬돼 있다. */
        Integer sortOrder,
        String floorName,
        List<UnityLocZoneResponse> zoneList
) {
    public static UnityLocFloorResponse from(UnityLocFloor floor) {
        return new UnityLocFloorResponse(
                floor.getFloorId(),
                floor.getFloorNum(),
                floor.getSortOrder(),
                floor.getFloorName(),
                floor.getZones().stream().map(UnityLocZoneResponse::from).toList()
        );
    }
}
