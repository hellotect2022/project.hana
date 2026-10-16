package com.mpole.hana.digitaltwin.dashboard.statistics.dto;

import lombok.Builder;

@Builder
public record StatisticsEnergyTrendPointResponse(
        String date,
        double kwh
) {
}
