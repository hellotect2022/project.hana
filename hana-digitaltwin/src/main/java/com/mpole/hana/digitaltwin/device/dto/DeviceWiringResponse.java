package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagramWiring;
import com.mpole.hana.digitaltwin.systemDiagram.entity.WiringSegment;

import java.util.List;

/**
 * 특정 디바이스 관점의 계통도 배선(wiring) 1건.
 * direction 은 이 디바이스가 배선의 FROM 인지 TO 인지, counterpart 는 반대쪽 장비다.
 */
public record DeviceWiringResponse(
        Long wiringId,
        String wiringName,
        String wiringType,
        String color,
        int sortOrder,
        String direction,              // "FROM" | "TO" (이 디바이스 기준)
        Long counterpartDeviceId,
        String counterpartDeviceName,
        Long diagramId,
        String diagramName,
        List<WiringSegment> segments
) {
    public static DeviceWiringResponse from(SystemDiagramWiring w, Long thisDeviceId) {
        boolean isFrom = w.getFromDevice() != null
                && w.getFromDevice().getDeviceId() != null
                && w.getFromDevice().getDeviceId().equals(thisDeviceId);
        var counterpart = isFrom ? w.getToDevice() : w.getFromDevice();
        return new DeviceWiringResponse(
                w.getWiringId(),
                w.getWiringName(),
                w.getWiringType(),
                w.getColor(),
                w.getSortOrder(),
                isFrom ? "FROM" : "TO",
                counterpart != null ? counterpart.getDeviceId() : null,
                counterpart != null ? counterpart.getDeviceName() : null,
                w.getDiagram() != null ? w.getDiagram().getDiagramId() : null,
                w.getDiagram() != null ? w.getDiagram().getDiagramName() : null,
                w.getSegments()
        );
    }
}
