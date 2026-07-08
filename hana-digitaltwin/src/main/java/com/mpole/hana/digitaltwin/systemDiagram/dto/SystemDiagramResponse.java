package com.mpole.hana.digitaltwin.systemDiagram.dto;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagram;
import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagramDevice;
import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagramWiring;
import com.mpole.hana.digitaltwin.systemDiagram.entity.WiringSegment;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

@Builder
public record SystemDiagramResponse(
        Long diagramId,
        String diagramName,
        String diagramCode,
        Long masterDeviceId,
        String masterDeviceName,
        String description,
        int sortOrder,
        Boolean active,
        List<DeviceItem> devices,
        List<WiringItem> wirings,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    @Builder
    public record DeviceItem(
            Long mappingId,
            Long deviceId,
            String deviceName,
            int sortOrder
    ) {
    }

    @Builder
    public record WiringItem(
            Long wiringId,
            String wiringName,
            String wiringType,
            String color,
            int sortOrder,
            Long fromDeviceId,
            String fromDeviceName,
            Long toDeviceId,
            String toDeviceName,
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

    public static SystemDiagramResponse from(SystemDiagram d) {
        if (d == null) return null;

        Device master = d.getMasterDevice();

        List<DeviceItem> deviceItems = d.getDevices().stream()
                .map(SystemDiagramResponse::toDeviceItem)
                .toList();

        List<WiringItem> wiringItems = d.getWirings().stream()
                .map(SystemDiagramResponse::toWiringItem)
                .toList();

        return SystemDiagramResponse.builder()
                .diagramId(d.getDiagramId())
                .diagramName(d.getDiagramName())
                .diagramCode(d.getDiagramCode())
                .masterDeviceId(master != null ? master.getDeviceId() : null)
                .masterDeviceName(master != null ? master.getDeviceName() : null)
                .description(d.getDescription())
                .sortOrder(d.getSortOrder())
                .active(d.getActive())
                .devices(deviceItems)
                .wirings(wiringItems)
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    private static DeviceItem toDeviceItem(SystemDiagramDevice m) {
        Device dev = m.getDevice();
        return DeviceItem.builder()
                .mappingId(m.getMappingId())
                .deviceId(dev != null ? dev.getDeviceId() : null)
                .deviceName(dev != null ? dev.getDeviceName() : null)
                .sortOrder(m.getSortOrder())
                .build();
    }

    private static WiringItem toWiringItem(SystemDiagramWiring w) {
        Device from = w.getFromDevice();
        Device to = w.getToDevice();

        List<SegmentItem> segments = w.getSegments() == null ? List.of()
                : w.getSegments().stream()
                        .map(SystemDiagramResponse::toSegmentItem)
                        .toList();

        return WiringItem.builder()
                .wiringId(w.getWiringId())
                .wiringName(w.getWiringName())
                .wiringType(w.getWiringType())
                .color(w.getColor())
                .sortOrder(w.getSortOrder())
                .fromDeviceId(from != null ? from.getDeviceId() : null)
                .fromDeviceName(from != null ? from.getDeviceName() : null)
                .toDeviceId(to != null ? to.getDeviceId() : null)
                .toDeviceName(to != null ? to.getDeviceName() : null)
                .segments(segments)
                .build();
    }

    private static SegmentItem toSegmentItem(WiringSegment s) {
        return SegmentItem.builder()
                .assetId(s.assetId())
                .start(toPointItem(s.start()))
                .end(toPointItem(s.end()))
                .build();
    }

    private static PointItem toPointItem(WiringSegment.Point3 p) {
        if (p == null) return null;
        return PointItem.builder()
                .x(p.x())
                .y(p.y())
                .z(p.z())
                .build();
    }
}
