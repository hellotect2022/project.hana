package com.mpole.hana.digitaltwin.buildingLighting.domain;

import java.time.OffsetDateTime;

/**
 * 개별 조명 장비의 실시간 해석 결과(불변 스냅샷).
 * <p>{@code tbl_device} + {@code tbl_device_placement}(층/구역) + {@code tbl_device_current_value}(포인트)
 * 를 조명 의미(점/소등, 디밍, 통신상태)로 파싱한 값. 운용현황·구역별·층별 집계의 공통 입력.
 */
public record LightingReading(
        Long deviceId,
        String deviceName,
        Long floorId,
        String floorName,
        Integer floorNum,
        Long zoneId,
        String zoneName,
        LightingState state,
        /** 디밍(밝기) 0~100. 디밍 미지원/미수신이면 null */
        Integer dimming,
        CommStatus commStatus,
        OffsetDateTime updatedAt
) {
    public boolean placedOnFloor() {
        return floorId != null;
    }

    public boolean placedInZone() {
        return zoneId != null;
    }
}
