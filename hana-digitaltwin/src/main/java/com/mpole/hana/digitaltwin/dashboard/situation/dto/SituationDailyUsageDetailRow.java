package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

/**
 * 통합현황 — 금일 전력·가스 KPI 상세 팝업 행 (누적 포인트 1건).
 */
@Builder
public record SituationDailyUsageDetailRow(
        Long deviceId,
        String deviceName,
        Long pointId,
        String tagName,
        String pointName,
        String unit,
        Double todayValue,
        boolean stale
) {
}
