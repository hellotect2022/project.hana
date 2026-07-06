package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

/**
 * 통합현황 상단 KPI 한 칸 — 프론트 {@code situationTopKpis} 항목과 대응.
 */
@Builder
public record SituationTopKpiItemResponse(
        String id,
        String label,
        String value,
        String unit,
        String tone,
        Integer run,
        Integer stop,
        Integer fault,
        Double temp,
        Double humidity
) {
}
