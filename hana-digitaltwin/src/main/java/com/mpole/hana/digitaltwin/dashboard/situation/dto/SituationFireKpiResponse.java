package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record SituationFireKpiResponse(
        int alarmCount,
        int sensorCount,
        List<SituationFireDeviceResponse> devices,
        boolean linked,
        String source
) {
}
