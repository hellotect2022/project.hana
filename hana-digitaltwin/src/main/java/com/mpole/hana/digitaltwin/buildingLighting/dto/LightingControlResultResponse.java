package com.mpole.hana.digitaltwin.buildingLighting.dto;

import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingControlCommand;

/**
 * 제어 명령 처리 결과.
 *
 * @param scope             ALL | FLOOR | ZONE | ZONE_DIMMING
 * @param targetId          층/구역 ID (ALL 이면 null)
 * @param command           점/소등 명령 (디밍이면 null)
 * @param dimming           디밍 0~100 (점/소등이면 null)
 * @param targetDeviceCount 제어 대상으로 식별된 장비 수
 * @param dispatched        연계 서비스가 명령을 수락했는지 여부
 */
public record LightingControlResultResponse(
        String scope,
        Long targetId,
        LightingControlCommand command,
        Integer dimming,
        int targetDeviceCount,
        boolean dispatched
) {
}
