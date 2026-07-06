package com.mpole.hana.digitaltwin.dashboard.power.dto;

import lombok.Builder;

import java.util.List;

/**
 * 전력 구간(특고압/저압/발전기) 패널 — 운용 현황 + 등록 현황.
 * <p>total/normal/fault는 통신상태(states)로 산출. alarm은 알람 소스 부재로 항상 0.</p>
 */
@Builder
public record PowerSectionResponse(
        String tabId,
        int total,
        int normal,
        int fault,
        int alarm,
        List<PowerSectionDeviceResponse> devices,
        boolean linked,
        String source
) {
}
