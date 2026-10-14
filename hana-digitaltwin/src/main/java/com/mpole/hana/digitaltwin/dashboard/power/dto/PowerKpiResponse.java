package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PowerKpiResponse(
        double cumulativeKwh,
        int runningCount,
        int meterTotal,
        Double todayKwh,
        Double weekKwh,
        Double monthKwh,
        boolean todayLinked,
        boolean weekLinked,
        boolean monthLinked,
        List<PowerRealtimePointResponse> realtimeSeries,
        List<PowerPeakDemandPointResponse> peakDemand,
        boolean mainChartsLinked,
        PowerPhaseBalanceResponse phaseBalance,
        String source,
        String usageSource,
        boolean stub
) {
}
