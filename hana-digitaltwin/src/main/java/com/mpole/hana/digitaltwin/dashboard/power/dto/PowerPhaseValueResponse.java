package com.mpole.hana.digitaltwin.dashboard.power.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * 상(phase)별 전압/전류 1개 항목.
 * <p>전압 행은 v만, 전류 행은 a만 채워 보낸다(프론트 valueKey 호환).</p>
 */
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PowerPhaseValueResponse(
        String phase,
        Double v,
        Double a
) {
}
