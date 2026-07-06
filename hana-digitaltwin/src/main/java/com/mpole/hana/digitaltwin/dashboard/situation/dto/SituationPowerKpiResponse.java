package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

/**
 * 통합현황 전력감시 패널 — 한전 인입(PS_PRD_SHV) 순시 계측값.
 * <p>전압/전류/순시전력/역률/주파수/피크는 실측, 누적 kWh는 누적값.
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
        boolean linked,
        String updatedAt,
        String source
) {
}
