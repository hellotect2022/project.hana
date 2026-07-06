package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

@Builder
public record PowerTabResponse(
        String id,
        String label
) {
}
