package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

/**
 * 통합현황 상단 KPI — 기준층 실내 환경(온·습도).
 * <p>항온항습기(HSE_CLG_CHT) temp/humi 포인트 평균.</p>
 */
@Builder
public record SituationIndoorEnvKpiResponse(
        Double temperature,
        Double humidity,
        int sampleCount,
        boolean linked,
        String source
) {
}
