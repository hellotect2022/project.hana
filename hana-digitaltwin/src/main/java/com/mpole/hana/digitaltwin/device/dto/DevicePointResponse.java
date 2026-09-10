package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.common.constant.PointDataSource;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;

/**
 * @param dataSource      값의 원천 수집 경로 코드. 미지정이면 null(= VIEW01 취급)
 * @param dataSourceLabel 화면 표시용 한글 라벨. 미지정이면 null
 */
public record DevicePointResponse(
        Long pointId,
        String pointKey,
        String tagName,
        String description,
        String pointDisplayName,
        String pointType,
        String unit,
        Boolean active,
        String refDeviceCode,
        String refPointCode,
        String dataSource,
        String dataSourceLabel
) {
    public static DevicePointResponse from(DevicePoint point) {
        PointDataSource source = point.getDataSource();
        return new DevicePointResponse(
                point.getPointId(),
                point.getPointKey(),
                point.getTagName(),
                point.getDescription(),
                point.getPointDisplayName(),
                point.getPointType(),
                point.getUnit(),
                point.getActive(),
                point.getRefDeviceCode(),
                point.getRefPointCode(),
                source != null ? source.name() : null,
                source != null ? source.getLabel() : null
        );
    }
}
