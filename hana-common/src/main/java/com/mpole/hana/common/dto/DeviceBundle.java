package com.mpole.hana.common.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record DeviceBundle(
        Long deviceId,
        OffsetDateTime timestamp,
        List<XnPointData> points
) {
}
