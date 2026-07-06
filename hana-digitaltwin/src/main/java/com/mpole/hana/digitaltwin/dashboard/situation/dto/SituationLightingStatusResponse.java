package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record SituationLightingStatusResponse(
        List<String> modes,
        Map<String, SituationLightingModeCountsResponse> byMode
) {
}
