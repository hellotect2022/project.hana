package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

@Builder
public record SituationEventKpiResponse(
        int todayCount,
        int completedCount,
        int openCount,
        boolean linked,
        String source
) {
}
