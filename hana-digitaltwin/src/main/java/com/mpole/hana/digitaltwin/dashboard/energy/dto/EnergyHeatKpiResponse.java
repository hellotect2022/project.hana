package com.mpole.hana.digitaltwin.dashboard.energy.dto;

import lombok.Builder;

/** 에너지 화면 — 지역난방(검침 등록 시 EnergySummary 연동 예정). */
@Builder
public record EnergyHeatKpiResponse(
        boolean linked,
        Double todayGcal,
        int meterTotal,
        String source
) {
}
