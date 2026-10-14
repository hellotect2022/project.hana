package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

@Builder
public record PowerPeakDemandPointResponse(
        String date,
        double kw
) {
}
