package com.mpole.hana.digitaltwin.dashboard.statistics.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record StatisticsInspectionResponse(
        String period,
        String system,
        long totalCount,
        boolean linked,
        List<StatisticsInspectionItemResponse> items,
        String source
) {
}
