package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

@Builder
public record SituationLightingModeCountsResponse(
        int on,
        int off,
        int alarm
) {
}
