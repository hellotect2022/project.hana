package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

/**
 * 전력 단일 계측값 + 정상/이상 판정.
 * <p>주파수·역률 등 임계값 기반 상태 표기에 사용.</p>
 */
@Builder
public record PowerMetricResponse(
        double value,
        String status,
        String label
) {
}
