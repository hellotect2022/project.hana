package com.mpole.hana.digitaltwin.dashboard.energy.dto;

import lombok.Builder;

/**
 * 에너지 화면 — 가스 누적 사용량(RM_GM G 합산).
 */
@Builder
public record EnergyGasKpiResponse(
        double cumulativeM3,
        int meterTotal,
        int measuringCount,
        boolean linked,
        String source
) {
}
