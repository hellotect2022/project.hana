package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

@Builder
public record PowerKpiResponse(
        double cumulativeKwh,
        int runningCount,
        int meterTotal,
        PowerPhaseBalanceResponse phaseBalance,
        String source,
        boolean stub
) {
}
