package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

@Builder
public record SituationTimeSeriesPointResponse(
        String t,
        int v
) {
}
