package com.mpole.hana.digitaltwin.dashboard.statistics.dto;

import lombok.Builder;

@Builder
public record StatisticsEventTrendPointResponse(
        String date,
        long count
) {
}
