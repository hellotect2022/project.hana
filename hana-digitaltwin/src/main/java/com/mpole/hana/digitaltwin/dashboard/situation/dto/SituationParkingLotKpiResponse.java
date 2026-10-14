package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

@Builder
public record SituationParkingLotKpiResponse(
        String floor,
        int occupied,
        int total
) {
}
