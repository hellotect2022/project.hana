package com.mpole.hana.digitaltwin.dashboard.mechanical.dto;

import lombok.Builder;

@Builder
public record MechRunStatusResponse(
        int operating,
        int stopped,
        int na,
        int alarm
) {
}
