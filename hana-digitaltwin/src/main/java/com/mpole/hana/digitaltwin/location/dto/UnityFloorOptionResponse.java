package com.mpole.hana.digitaltwin.location.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;

/** 층 선택용 옵션 응답 (콜라이더 등록/필터 드롭다운) */
public record UnityFloorOptionResponse(
        Long floorId,
        Integer floorNum,
        String floorName
) {
    public static UnityFloorOptionResponse from(UnityLocFloor floor) {
        return new UnityFloorOptionResponse(floor.getFloorId(), floor.getFloorNum(), floor.getFloorName());
    }
}
