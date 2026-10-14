package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record SituationParkingKpiResponse(
        int occupied,
        int total,
        List<SituationParkingLotKpiResponse> lots,
        long todayInCount,
        long todayOutCount,
        boolean linked,
        String source
) {
}
