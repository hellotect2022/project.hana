package com.mpole.hana.digitaltwin.dashboard.mechanical.dto;

import lombok.Builder;

@Builder
public record MechCategoryMixResponse(
        String id,
        String name,
        int count,
        int value
) {
}
