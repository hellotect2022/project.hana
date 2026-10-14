package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PowerMccRuntimeResponse(
        List<PowerMccRuntimeItemResponse> items,
        int total,
        int linked,
        String source
) {
}
