package com.mpole.hana.digitaltwin.dashboard.statistics.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record StatisticsEnergyTrendResponse(
        String period,
        List<StatisticsEnergyTrendPointResponse> points,
        boolean linked,
        String source
) {
}
