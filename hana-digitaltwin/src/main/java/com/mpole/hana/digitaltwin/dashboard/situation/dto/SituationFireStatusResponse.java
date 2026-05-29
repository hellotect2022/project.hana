package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record SituationFireStatusResponse(
        int alarmCount,
        int sensorCount,
        List<SituationFireDeviceResponse> devices
) {
}
