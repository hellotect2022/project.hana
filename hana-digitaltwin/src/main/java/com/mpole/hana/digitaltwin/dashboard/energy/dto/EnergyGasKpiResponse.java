package com.mpole.hana.digitaltwin.dashboard.energy.dto;

import lombok.Builder;

/**
 * 에너지 화면 — 가스(RM_GM). 누적(current_value G) + 금일 사용량(EnergySummary).
 */
@Builder
public record EnergyGasKpiResponse(
        double cumulativeM3,
        Double todayNm3,
        Double weekNm3,
        Double monthNm3,
        int meterTotal,
        int measuringCount,
        boolean linked,
        boolean todayLinked,
        boolean weekLinked,
        boolean monthLinked,
        String source,
        String todaySource
) {
}
