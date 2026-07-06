package com.mpole.hana.digitaltwin.buildingLighting.dto;

import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingControlCommand;
import jakarta.validation.constraints.NotNull;

/** 점/소등 제어 요청 (전 구역 / 특정 층 / 특정 구역 공통). */
public record LightingControlRequest(
        @NotNull(message = "command 는 필수입니다. (ON 또는 OFF)")
        LightingControlCommand command
) {
}
