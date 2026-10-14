package com.mpole.hana.digitaltwin.space.dto;

import jakarta.validation.constraints.NotNull;

/** 표현정책 일괄 저장(정책 저장) 항목 — 표시 속성만 수정 */
public record ZonePolicyUpdateItem(
        @NotNull Long ruleId,
        String displayName,
        String colorCode,
        Integer opacity,
        Boolean active,
        Boolean evacuationAutoShow
) {
}
