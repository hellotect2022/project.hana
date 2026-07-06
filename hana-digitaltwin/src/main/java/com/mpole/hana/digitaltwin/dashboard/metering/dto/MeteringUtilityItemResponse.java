package com.mpole.hana.digitaltwin.dashboard.metering.dto;

import lombok.Builder;

@Builder
public record MeteringUtilityItemResponse(
        String id,
        String label,
        int measuring,
        int disconnected,
        int total,
        boolean linked,
        String note
) {
}
