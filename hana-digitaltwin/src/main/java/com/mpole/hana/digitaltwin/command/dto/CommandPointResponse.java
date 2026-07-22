package com.mpole.hana.digitaltwin.command.dto;

import com.mpole.hana.digitaltwin.command.entity.CommandPoint;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * 디바이스 제어 포인트 응답. 제어 대상 포인트의 tagName/ref 코드까지 함께 노출한다.
 */
@Builder
public record CommandPointResponse(
        Long commandPointId,
        Long deviceId,
        String deviceName,
        Long pointId,
        String tagName,
        String refDeviceCode,
        String refPointCode,
        String label,
        String onValue,
        String offValue,
        int sortOrder,
        Boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CommandPointResponse from(CommandPoint c) {
        if (c == null) return null;
        Device d = c.getDevice();
        DevicePoint p = c.getPoint();
        return CommandPointResponse.builder()
                .commandPointId(c.getCommandPointId())
                .deviceId(d != null ? d.getDeviceId() : null)
                .deviceName(d != null ? d.getDeviceName() : null)
                .pointId(p != null ? p.getPointId() : null)
                .tagName(p != null ? p.getTagName() : null)
                .refDeviceCode(p != null ? p.getRefDeviceCode() : null)
                .refPointCode(p != null ? p.getRefPointCode() : null)
                .label(c.getLabel())
                .onValue(c.getOnValue())
                .offValue(c.getOffValue())
                .sortOrder(c.getSortOrder())
                .active(c.getActive())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
