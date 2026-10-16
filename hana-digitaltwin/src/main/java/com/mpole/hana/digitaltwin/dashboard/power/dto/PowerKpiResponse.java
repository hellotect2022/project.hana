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
        /** D-6 ~ D-0 일별 kWh (전력·에너지·통계 주간 추이 공통). */
        List<PowerDailyKwhPointResponse> weekDailyKwh,
        /** 당월 1일 ~ 오늘 일별 kWh (통계 월간 추이). */
        List<PowerDailyKwhPointResponse> monthDailyKwh,
        boolean mainChartsLinked,
        PowerPhaseBalanceResponse phaseBalance,
        String source,
        String usageSource,
        boolean stub
) {
}
