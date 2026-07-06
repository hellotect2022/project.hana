package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

import java.util.List;

/**
 * R·S·T 상별 전압·전류 균형 — 한전 인입(PS_PRD_SHV) 순시 계측값.
 * <p>선간전압(R-S/S-T/T-R), 상전류(R/S/T), 주파수(HZ), 역률(PF).</p>
 */
@Builder
public record PowerPhaseBalanceResponse(
        PowerMetricResponse frequency,
        PowerMetricResponse powerFactor,
        List<PowerPhaseValueResponse> voltage,
        List<PowerPhaseValueResponse> current
) {
}
