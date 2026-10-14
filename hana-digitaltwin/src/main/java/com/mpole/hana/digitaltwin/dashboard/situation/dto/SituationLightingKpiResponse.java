package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record SituationLightingKpiResponse(
        List<String> modes,
        Map<String, SituationLightingModeCountsResponse> byMode,
        boolean linked,
        String source
) {
}
