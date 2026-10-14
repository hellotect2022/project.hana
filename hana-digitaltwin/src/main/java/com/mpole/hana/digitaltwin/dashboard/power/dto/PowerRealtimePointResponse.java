package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

@Builder
public record PowerRealtimePointResponse(
        String t,
        double kwh
) {
}
