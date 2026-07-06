package com.mpole.hana.digitaltwin.dashboard.mechanical.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record MechanicalSituationTabResponse(
        String id,
        String label,
        List<MechanicalSituationGroupResponse> groups
) {
}
