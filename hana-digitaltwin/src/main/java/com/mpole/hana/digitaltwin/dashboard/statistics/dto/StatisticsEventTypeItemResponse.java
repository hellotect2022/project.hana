package com.mpole.hana.digitaltwin.dashboard.statistics.dto;

import lombok.Builder;

@Builder
public record StatisticsEventTypeItemResponse(
        String name,
        long value,
        int pct,
        String levelCode
) {
}
