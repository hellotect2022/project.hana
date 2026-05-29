package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

@Builder
public record SituationFireDeviceResponse(
        String id,
        String label,
        int count
) {
}
