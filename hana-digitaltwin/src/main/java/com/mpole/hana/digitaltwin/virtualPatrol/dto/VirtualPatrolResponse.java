package com.mpole.hana.digitaltwin.virtualPatrol.dto;

import com.mpole.hana.digitaltwin.virtualPatrol.entity.VirtualPatrolRoute;
import com.mpole.hana.digitaltwin.virtualPatrol.entity.VirtualPatrolWaypoint;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 가상순찰 경로 응답. Unity 클라이언트가 쓰는 {routeName, bookmarkSlot, waypoints} +
 * 식별/시각 메타(routeId, createdAt, updatedAt) 포함.
 */
public record VirtualPatrolResponse(
        String routeId,
        String displayName,
        Integer bookmarkSlot,
        List<VirtualPatrolWaypoint> waypoints,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static VirtualPatrolResponse from(VirtualPatrolRoute e) {
        return new VirtualPatrolResponse(
                e.getRouteId(),
                e.getDisplayName(),
                e.getBookmarkSlot(),
                e.getWaypoints() != null ? e.getWaypoints() : List.of(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
