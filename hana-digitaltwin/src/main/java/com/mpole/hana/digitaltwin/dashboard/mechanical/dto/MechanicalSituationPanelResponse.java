package com.mpole.hana.digitaltwin.dashboard.mechanical.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record MechanicalSituationPanelResponse(
        List<MechanicalSituationTabResponse> tabs,
        String source,
        boolean stub
) {
}
