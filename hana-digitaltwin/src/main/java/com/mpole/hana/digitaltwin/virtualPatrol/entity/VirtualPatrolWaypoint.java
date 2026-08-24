package com.mpole.hana.digitaltwin.virtualPatrol.entity;

/**
 * 가상순찰 경로의 개별 지점(waypoint). {@code tbl_virtual_patrol_route.waypoints} JSONB 배열 요소.
 * <p>Unity PC 클라이언트 형식과 필드명을 그대로 맞춘다: 위치 {@code x,y,z} + 회전 쿼터니언 {@code qx,qy,qz,qw}.</p>
 */
public record VirtualPatrolWaypoint(
        Integer seq,
        Double x,
        Double y,
        Double z,
        Double qx,
        Double qy,
        Double qz,
        Double qw,
        Integer floorIndex,
        String placeLabel
) {
}
