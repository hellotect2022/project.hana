package com.mpole.hana.digitaltwin.realtime.dto;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceValueHistory;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 디바이스 실시간 값 이력 1건(= 특정 시각의 device bundle 스냅샷).
 */
public record DeviceValueHistoryResponse(
        Long deviceId,
        String deviceKey,
        OffsetDateTime createdAt,
        List<XnPointData> points
) {
    public static DeviceValueHistoryResponse from(DeviceValueHistory h) {
        return new DeviceValueHistoryResponse(
                h.getDeviceId(), h.getDeviceKey(), h.getCreatedAt(), h.getPoints());
    }
}
