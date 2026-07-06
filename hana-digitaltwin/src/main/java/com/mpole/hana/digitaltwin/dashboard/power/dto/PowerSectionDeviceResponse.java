package com.mpole.hana.digitaltwin.dashboard.power.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * 전력 구간 등록 현황 1행 — 설비 1대의 전압/전류/전력 + 통신상태.
 */
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PowerSectionDeviceResponse(
        String id,
        Double v,
        Double a,
        Double kw,
        String comm
) {
}
