package com.mpole.hana.common.dto;

import com.mpole.hana.common.constant.PointType;
import lombok.Builder;

@Builder
public record DeviceRawPointData(
        String pointKey,
        Long pointId,
        Long deviceId,
        String deviceCode,
        String pointCode,
        String schemaTagName,
        PointType pointType,
        String unit,
        boolean isAlarmSource
) {
}
