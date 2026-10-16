package com.mpole.hana.digitaltwin.dashboard.statistics.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record StatisticsEventTypeResponse(
        String period,
        long totalCount,
        boolean linked,
        List<StatisticsEventTypeItemResponse> items,
        String source
) {
}
