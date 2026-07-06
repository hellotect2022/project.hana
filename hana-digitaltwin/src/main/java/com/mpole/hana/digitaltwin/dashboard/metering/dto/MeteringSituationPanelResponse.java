package com.mpole.hana.digitaltwin.dashboard.metering.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record MeteringSituationPanelResponse(
        List<MeteringUtilityItemResponse> utilities,
        String source,
        boolean stub
) {
}
