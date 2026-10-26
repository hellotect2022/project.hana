package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

/**
 * 통합현황 전력감시 패널 — 한전 인입(PS_PRD_SHV) 순시 계측값.
 * <p>순시전력/역률/주파수·유효·무효전력량·현재피크는 PS_PRD_SHV 태그 실측.
 * 시계열 추이·전일대비·피크 발생시각은 이력 테이블 부재로 미제공.</p>
 */
@Builder
public record SituationPowerKpiResponse(
        Double kw,
        Double voltage,
        Double current,
        Double frequency,
        Double powerFactor,
        Double peak,
        Double cumulativeKwh,
        /** 유효전력량 (MWh) — APH·kWh 태그 kWh → MWh */
        Double activeEnergyMwh,
        /** 무효전력량 (MVarh) — RPH 태그 kVarh → MVarh */
        Double reactiveEnergyMvarh,
        /** 현재피크 (kWh) — peak-w 등 */
        Double currentPeakKwh,
        boolean linked,
        String updatedAt,
        String source
) {
}
