package com.mpole.hana.digitaltwin.buildingLighting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 특정 구역 디밍(밝기) 조절 요청. */
public record LightingDimmingRequest(
        @NotNull(message = "level 은 필수입니다.")
        @Min(value = 0, message = "level 은 0 이상이어야 합니다.")
        @Max(value = 100, message = "level 은 100 이하여야 합니다.")
        Integer level
) {
}
