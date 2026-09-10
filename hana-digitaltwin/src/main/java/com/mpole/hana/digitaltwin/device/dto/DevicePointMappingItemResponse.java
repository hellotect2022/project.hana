package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;

public record DevicePointMappingItemResponse(
        Long pointId,
        Long deviceId,
        String deviceName,
        String deviceKey,
        String categoryPath,
        String pointKey,
        String tagName,
        String description,
        String pointDisplayName,
        String pointType,
        String unit,
        Boolean active,
        String refDeviceCode,
        String refPointCode,
        /** 값의 원천 수집 경로 코드. 미지정이면 null(= VIEW01 취급) */
        String dataSource,
        /** 화면 표시용 한글 라벨 */
        String dataSourceLabel
) {
    public static DevicePointMappingItemResponse from(DevicePoint point) {
        Device device = point.getDevice();
        com.mpole.hana.common.constant.PointDataSource source = point.getDataSource();
        String categoryPath = device != null && device.getCategory() != null
                ? DeviceCategoryResponse.buildCategoryPath(device.getCategory())
                : null;
        return new DevicePointMappingItemResponse(
                point.getPointId(),
                device != null ? device.getDeviceId() : null,
                device != null ? device.getDeviceName() : null,
                device != null ? device.getDeviceKey() : null,
                categoryPath,
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
