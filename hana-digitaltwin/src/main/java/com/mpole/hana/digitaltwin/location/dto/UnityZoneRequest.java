package com.mpole.hana.digitaltwin.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 콜라이더(존) 등록·수정 요청 */
public record UnityZoneRequest(
        @NotNull(message = "층(floorId)은 필수입니다.") Long floorId,
        @NotBlank(message = "이름(name)은 필수입니다.") @Size(max = 200) String zoneName,
        @NotBlank(message = "mesh_name은 필수입니다.") @Size(max = 200) String zoneMeshName,
        /** "2D" | "3D" — 미지정 시 2D */
        @Size(max = 8) String meshType
) {
}
