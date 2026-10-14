package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

/**
 * 통합현황 상단 — 금일 전력·가스 사용량 (누적 포인트 + tbl_energy_snapshot).
 */
@Builder
public record SituationDailyUsageKpiResponse(
        Double todayPowerMwh,
        Double todayGasNm3,
        boolean powerLinked,
        boolean gasLinked,
        int powerMeterCount,
        int gasMeterCount,
        int powerPointCount,
        int gasPointCount,
        String powerSource,
        String gasSource
) {
}
