package com.mpole.hana.digitaltwin.dashboard.mechanical.dto;

import lombok.Builder;

@Builder
public record MechEquipGroupResponse(
        String id,
        String name,
        int total,
        int operating,
        int stopped,
        int na,
        int alarm
) {
}
