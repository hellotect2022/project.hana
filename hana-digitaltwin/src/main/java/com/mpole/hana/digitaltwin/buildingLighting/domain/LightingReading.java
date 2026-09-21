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
        /**
         * 층 표시 정렬 순서(0 = 최하층). 층별·구역별 정렬 기준.
         * <p>{@code floorNum} 으로 정렬하면 층수가 없는 옥외({@code floor_num = 100})가
         * PH2F 뒤로 밀린다.</p>
         */
        Integer floorSortOrder,
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
