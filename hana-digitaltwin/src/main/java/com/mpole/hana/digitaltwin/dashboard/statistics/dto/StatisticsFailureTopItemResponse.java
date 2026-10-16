package com.mpole.hana.digitaltwin.dashboard.statistics.dto;

import lombok.Builder;

@Builder
public record StatisticsFailureTopItemResponse(
        String id,
        int rank,
        String name,
        long count,
        String system,
        String location,
        String lastOccurred
) {
}
