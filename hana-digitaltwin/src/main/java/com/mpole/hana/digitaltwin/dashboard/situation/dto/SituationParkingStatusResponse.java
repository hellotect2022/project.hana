package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

@Builder
public record SituationParkingStatusResponse(
        int occupied,
        int total
) {
}
