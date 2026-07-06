package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

import java.util.List;

/**
 * 전력감시 화면 스냅샷 — 상세 KPI·차트 DTO는 연동 단계에서 분리 추가 예정.
 */
@Builder
public record PowerPageSnapshotResponse(
        List<PowerTabResponse> tabs,
        String activeTabId,
        boolean stub
) {
}
