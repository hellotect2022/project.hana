package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

@Builder
public record SituationMechanicalEquipmentResponse(
        String id,
        String name,
        int run,
        int stop,
        int alarm
) {
}
