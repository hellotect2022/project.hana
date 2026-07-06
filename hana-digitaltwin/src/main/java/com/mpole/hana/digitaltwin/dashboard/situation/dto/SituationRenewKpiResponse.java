package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

import java.util.List;

/**
 * 통합현황 상단 KPI — 신재생 에너지 생산(누적).
 * <p>태양광(RE_PV ceg) + 지열(RE_GS chg) 누적발전량 합산 및 설비별 내역.</p>
 */
@Builder
public record SituationRenewKpiResponse(
        double productionKwh,
        double solarKwh,
        double geoKwh,
        int deviceCount,
        List<SituationRenewDeviceResponse> devices,
        boolean linked,
        String source
) {
}
