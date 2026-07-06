package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record SituationPowerMonitorResponse(
        int kw,
        int voltage,
        int frequency,
        int peak,
        double powerFactor,
        double dailyMwh,
        double vsYesterdayPct,
        String demandOccurTime,
        List<SituationTimeSeriesPointResponse> series
) {
}
