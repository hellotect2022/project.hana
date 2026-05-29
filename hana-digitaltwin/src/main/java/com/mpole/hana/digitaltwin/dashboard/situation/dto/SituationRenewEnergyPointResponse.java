package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

@Builder
public record SituationRenewEnergyPointResponse(
        String t,
        int solar,
        int geo
) {
}
