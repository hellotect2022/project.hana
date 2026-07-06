package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

@Builder
public record SituationMeteringUtilityResponse(
        String id,
        String label,
        int measuring,
        int disconnected
) {
}
