package com.mpole.hana.digitaltwin.dashboard.statistics.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record StatisticsEventTrendResponse(
        String period,
        List<StatisticsEventTrendPointResponse> points,
        boolean linked,
        long totalCount,
        String source
) {
}
