package com.mpole.hana.digitaltwin.dashboard.mechanical.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record MechanicalPageBoardResponse(
        String tabId,
        boolean linked,
        int total,
        List<MechCategoryMixResponse> categoryMix,
        MechRunStatusResponse runStatus,
        List<MechEquipGroupResponse> equipBars,
        String source
) {
}
