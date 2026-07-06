package com.mpole.hana.digitaltwin.dashboard.mechanical.dto;

import lombok.Builder;

@Builder
public record MechanicalEquipmentKpiResponse(
        int run,
        int stop,
        int unknown,
        int total,
        String source,
        boolean stub
) {
}
