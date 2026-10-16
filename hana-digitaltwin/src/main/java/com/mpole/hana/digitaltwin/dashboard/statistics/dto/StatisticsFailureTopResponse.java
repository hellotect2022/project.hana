package com.mpole.hana.digitaltwin.dashboard.statistics.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record StatisticsFailureTopResponse(
        String period,
        String system,
        boolean linked,
        List<StatisticsFailureTopItemResponse> top10,
        List<StatisticsFailureTopItemResponse> rows,
        String source
) {
}
