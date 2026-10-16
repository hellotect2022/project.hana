package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

/** 최근 7일(또는 구간) 일별 전력 사용량 kWh — 스냅샷 15분 Δ 합산. */
@Builder
public record PowerDailyKwhPointResponse(
        String date,
        double kwh
) {
}
