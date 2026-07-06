package com.mpole.hana.digitaltwin.dashboard.situation.dto;

import lombok.Builder;

/**
 * 신재생 설비 1대의 누적 발전량.
 * <p>type: solar(태양광 ceg) / geo(지열 chg)</p>
 */
@Builder
public record SituationRenewDeviceResponse(
        String name,
        String type,
        double kwh
) {
}
