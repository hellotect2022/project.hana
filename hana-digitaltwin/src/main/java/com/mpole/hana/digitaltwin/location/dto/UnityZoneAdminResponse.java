package com.mpole.hana.digitaltwin.location.dto;

import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;

import java.util.Map;

/**
 * 콜라이더(구역) 관리 화면용 응답 — 층 정보 포함.
 *
 * <p>계층 도입으로 필드가 <b>추가만</b> 됐다. 기존 필드는 이름·의미 모두 그대로라
 * 이 응답을 쓰던 클라이언트는 수정 없이 동작한다.</p>
 *
 * @param parentId   상위 구역 id. null 이면 최상위. 신규
 * @param parentName 상위 구역 이름(화면 표시용). 신규
 * @param depth      0=최상위, 1=자식. 신규
 * @param zoneCode   외부 연동 식별자. 신규
 * @param attributes 구역 종류별 부가 속성. 신규
 */
public record UnityZoneAdminResponse(
        Long zoneId,
        Long floorId,
        Integer floorNum,
        String floorName,
        String zoneName,
        String zoneMeshName,
        String meshType,
        Long parentId,
        String parentName,
        Integer depth,
        String zoneCode,
        Map<String, Object> attributes
) {
    public static UnityZoneAdminResponse from(UnityLocZone zone) {
        var floor = zone.getFloor();
        var parent = zone.getParent();
        return new UnityZoneAdminResponse(
                zone.getZoneId(),
                floor != null ? floor.getFloorId() : null,
                floor != null ? floor.getFloorNum() : null,
                floor != null ? floor.getFloorName() : null,
                zone.getZoneName(),
                zone.getZoneMeshName(),
                zone.getMeshType(),
                parent != null ? parent.getZoneId() : null,
                parent != null ? parent.getZoneName() : null,
                zone.getDepth(),
                zone.getZoneCode(),
                zone.getAttributes()
        );
    }
}
