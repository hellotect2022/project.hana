package com.mpole.hana.digitaltwin.systemDiagram.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record SystemDiagramRequest(
        String diagramName,
        String diagramCode,
        Long masterDeviceId,
        String description,
        int sortOrder,
        Boolean active,
        List<DeviceItem> devices,
        List<WiringItem> wirings
) {
    @Builder
    public record DeviceItem(
            Long deviceId,
            int sortOrder
    ) {
    }

    @Builder
    public record WiringItem(
            String wiringName,
            String wiringType,
            String color,
            int sortOrder,
            Long fromDeviceId,
            Long toDeviceId,
            List<SegmentItem> segments
    ) {
    }

    @Builder
    public record SegmentItem(
            Long assetId,
            PointItem start,
            PointItem end
    ) {
    }

    @Builder
    public record PointItem(
            Float x,
            Float y,
            Float z
    ) {
    }
}
