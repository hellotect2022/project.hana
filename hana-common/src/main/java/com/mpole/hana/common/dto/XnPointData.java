package com.mpole.hana.common.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder(toBuilder = true)
public record XnPointData(
        Long deviceId, // 우리쪽 DB 의 deviceId
        Long pointId,  // 우리쪽 DB 의 pointId
        String deviceCode,
        String pointCode,
        String alarmYn,
        String objectType,
        String pointName,
        String valueRaw,
        String unit,
        String tagName,
        OffsetDateTime updateDatetime
) {
}
