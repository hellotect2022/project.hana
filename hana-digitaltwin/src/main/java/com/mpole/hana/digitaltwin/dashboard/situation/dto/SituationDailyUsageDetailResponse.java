package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

import java.util.List;

/**
 * 통합현황 — 금일 전력·가스 사용량 설비·포인트별 목록.
 *
 * @param type power | gas — KPI 타일 id와 동일
 */
@Builder
public record SituationDailyUsageDetailResponse(
        String type,
        String source,
        List<SituationDailyUsageDetailRow> rows
) {
}
