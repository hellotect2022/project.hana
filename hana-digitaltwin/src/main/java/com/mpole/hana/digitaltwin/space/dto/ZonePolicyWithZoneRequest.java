package com.mpole.hana.digitaltwin.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 조명/화재 구역 추가 — 새 Zone(위치) + 표현정책을 한 번에 생성하는 요청.
 * Zone 은 항상 3D(meshType) 로 저장된다.
 */
public record ZonePolicyWithZoneRequest(
        @NotNull(message = "floorId 는 필수입니다.") Long floorId,
        @NotBlank(message = "zoneName 은 필수입니다.") @Size(max = 200) String zoneName,
        @NotBlank(message = "mesh_name 은 필수입니다.") @Size(max = 200) String zoneMeshName,
        @NotBlank(message = "systemType 은 필수입니다.") String systemType,
        @Size(max = 200) String displayName,
        @Size(max = 16) String colorCode,
        Integer opacity,
        Boolean active,
        Boolean evacuationAutoShow
) {
}
