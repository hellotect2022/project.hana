package com.mpole.hana.digitaltwin.dashboard.mechanical.dto;

import lombok.Builder;

@Builder
public record MechanicalTabResponse(
        String id,
        String label
) {
}
