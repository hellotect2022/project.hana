package com.mpole.hana.digitaltwin.virtualPatrol.dto;

import com.mpole.hana.digitaltwin.virtualPatrol.entity.VirtualPatrolWaypoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 가상순찰 경로 생성/수정 요청. 수정은 전체 교체(full replace).
 * <p>{@code routeId} 는 클라이언트(Unity)가 생성한 UUID — 생성 시 body 로 전달(수정 시엔 path 값 사용).
 * {@code bookmarkSlot} null = 즐겨찾기 미지정/해제.</p>
 */
public record VirtualPatrolRequest(
        String routeId,
        @NotBlank String displayName,
        Integer bookmarkSlot,
        @Valid List<VirtualPatrolWaypoint> waypoints
) {
}
