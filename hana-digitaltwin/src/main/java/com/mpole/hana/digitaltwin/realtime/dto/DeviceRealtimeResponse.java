package com.mpole.hana.digitaltwin.realtime.dto;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceSnapshotLast;

import java.time.OffsetDateTime;
import java.util.List;

public record DeviceRealtimeResponse(
        Long deviceId,
        OffsetDateTime timestamp,
        List<XnPointData> points
) {
    public static DeviceRealtimeResponse from(DeviceSnapshotLast snapshot) {
        return new DeviceRealtimeResponse(
                snapshot.getDeviceId(),
                snapshot.getUpdatedAt(),
                snapshot.getPoints()
        );
    }
}
