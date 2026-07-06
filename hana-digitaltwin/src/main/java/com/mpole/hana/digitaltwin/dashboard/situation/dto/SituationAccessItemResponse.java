package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

@Builder
public record SituationAccessItemResponse(
        String key,
        String label,
        int value
) {
}
