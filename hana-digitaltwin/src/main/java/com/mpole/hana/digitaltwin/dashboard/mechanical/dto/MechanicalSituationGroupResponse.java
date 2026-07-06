package com.mpole.hana.digitaltwin.dashboard.mechanical.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record MechanicalSituationGroupResponse(
        String id,
        String name,
        int run,
        int stop,
        int unknown,
        int total,
        List<MechanicalSituationDeviceResponse> devices
) {
}
