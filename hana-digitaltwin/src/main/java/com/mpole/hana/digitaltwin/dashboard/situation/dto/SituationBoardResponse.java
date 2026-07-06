package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 통합현황 패널 일괄 응답 — 프론트 {@code dashboardSituationBoardMock} 구조와 대응.
 */
@Builder
public record SituationBoardResponse(
        List<SituationTopKpiItemResponse> topKpis,
        List<String> mechanicalTabs,
        Map<String, List<SituationMechanicalEquipmentResponse>> mechanicalEquipmentByTab,
        SituationPowerMonitorResponse powerMonitor,
        SituationFireStatusResponse fireStatus,
        List<SituationAccessItemResponse> accessStatus,
        List<SituationMeteringUtilityResponse> meteringUtilities,
        SituationLightingStatusResponse lightingStatus,
        SituationParkingStatusResponse parkingStatus,
        List<SituationRenewEnergyPointResponse> energyRenew
) {
}
