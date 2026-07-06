package com.mpole.hana.digitaltwin.buildingLighting.port;

import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingControlCommand;

import java.util.List;

/**
 * 연계(SI) 서비스로 내보낼 조명 제어 명령 페이로드.
 *
 * @param scope        ALL | FLOOR | ZONE | ZONE_DIMMING
 * @param targetId     층/구역 ID (ALL 이면 null)
 * @param command      점/소등 명령 (디밍이면 null)
 * @param dimmingLevel 디밍 0~100 (점/소등이면 null)
 * @param deviceIds    제어 대상 장비 ID 목록
 */
public record LightingControlMessage(
        String scope,
        Long targetId,
        LightingControlCommand command,
        Integer dimmingLevel,
        List<Long> deviceIds
) {
    public static LightingControlMessage all(LightingControlCommand command, List<Long> deviceIds) {
        return new LightingControlMessage("ALL", null, command, null, deviceIds);
    }

    public static LightingControlMessage floor(Long floorId, LightingControlCommand command, List<Long> deviceIds) {
        return new LightingControlMessage("FLOOR", floorId, command, null, deviceIds);
    }

    public static LightingControlMessage zone(Long zoneId, LightingControlCommand command, List<Long> deviceIds) {
        return new LightingControlMessage("ZONE", zoneId, command, null, deviceIds);
    }

    public static LightingControlMessage zoneDimming(Long zoneId, Integer level, List<Long> deviceIds) {
        return new LightingControlMessage("ZONE_DIMMING", zoneId, null, level, deviceIds);
    }
}
