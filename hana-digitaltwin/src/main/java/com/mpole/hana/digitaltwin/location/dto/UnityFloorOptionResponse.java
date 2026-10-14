package com.mpole.hana.digitaltwin.location.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;

/** 층 선택용 옵션 응답 (콜라이더 등록/필터 드롭다운) */
public record UnityFloorOptionResponse(
        Long floorId,
        Integer floorNum,
        /** 표시 정렬 순서. 0 = 최하층(B7F) → 24 = PH2F. 목록은 이미 이 순서다. */
        Integer sortOrder,
        String floorName,
        String description
) {
    public static UnityFloorOptionResponse from(UnityLocFloor floor) {
        return new UnityFloorOptionResponse(
                floor.getFloorId(), floor.getFloorNum(), floor.getSortOrder(), floor.getFloorName(), floor.getDescription());
    }
}
