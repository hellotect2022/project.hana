package com.mpole.hana.digitaltwin.location.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;

/** 콜라이더(존) 관리 화면용 응답 — 층 정보 포함 */
public record UnityZoneAdminResponse(
        Long zoneId,
        Long floorId,
        Integer floorNum,
        String floorName,
        String zoneName,
        String zoneMeshName,
        String meshType
) {
    public static UnityZoneAdminResponse from(UnityLocZone zone) {
        var floor = zone.getFloor();
        return new UnityZoneAdminResponse(
                zone.getZoneId(),
                floor != null ? floor.getFloorId() : null,
                floor != null ? floor.getFloorNum() : null,
                floor != null ? floor.getFloorName() : null,
                zone.getZoneName(),
                zone.getZoneMeshName(),
                zone.getMeshType()
        );
    }
}
