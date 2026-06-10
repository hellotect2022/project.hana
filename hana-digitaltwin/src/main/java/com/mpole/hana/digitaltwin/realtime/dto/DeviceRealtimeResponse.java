package com.mpole.hana.digitaltwin.realtime.dto;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.device.dto.DeviceLocationResponse;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;

import java.time.OffsetDateTime;
import java.util.List;

public record DeviceRealtimeResponse(
        Long deviceId,
        OffsetDateTime timestamp,
        List<XnPointData> points
) {
    public static DeviceRealtimeResponse from(DeviceCurrentValue currentValue) {
        if (currentValue == null) return null;
        return new DeviceRealtimeResponse(
                currentValue.getDeviceId(),
                currentValue.getUpdatedAt(),
                currentValue.getPoints()
        );
    }

    private static DeviceRealtimeResponse empty() {
        return new DeviceRealtimeResponse(null, null, null);
    }
}
