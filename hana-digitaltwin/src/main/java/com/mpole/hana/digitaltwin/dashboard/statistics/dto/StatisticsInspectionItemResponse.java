package com.mpole.hana.digitaltwin.dashboard.statistics.dto;

import lombok.Builder;

@Builder
public record StatisticsInspectionItemResponse(
        String name,
        long value,
        int pct
) {
}
