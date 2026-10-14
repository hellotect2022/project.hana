package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.entity.DevicePlacement;
import com.mpole.hana.digitaltwin.device.property.DevicePropertyInfo;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaIndex;
import com.mpole.hana.digitaltwin.realtime.dto.DeviceCurrentValueResponse;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public record DeviceBySystemResponse(
        Long deviceId,
        String deviceName,
        String description,
        Boolean active,
        Long categoryId,
        String categoryName,
        String categoryPath,
        Long assetId,          // category.defaultAsset
        String assetName,
        DeviceTransformResponse transform,
        DeviceLocationResponse location,
        boolean placed,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        DevicePropertyInfo propertyInfo,
        DeviceCurrentValueResponse currentValue
) {
    public static DeviceBySystemResponse from(Device d) {
        DeviceCategory cat = d.getCategory();
        UnityAsset asset = cat != null ? cat.getDefaultAsset() : null;
        DevicePlacement p = d.getPlacement();
        DeviceCurrentValue c = d.getDeviceCurrentValue();

        return new DeviceBySystemResponse(
                d.getDeviceId(),
                d.getDeviceName(),
                d.getDescription(),
                d.getActive(),
                cat != null ? cat.getCategoryId() : null,
                cat != null ? cat.getCategoryName() : null,
                buildCategoryPath(cat),
                asset != null ? asset.getAssetId() : null,
                asset != null ? asset.getAssetName() : null,
                DeviceTransformResponse.from(p),
                DeviceLocationResponse.from(p),
                p != null && p.isPlaced(),
                d.getCreatedAt(),
                d.getUpdatedAt()
                ,d.getPropertyInfo()
                , DeviceCurrentValueResponse.from(c, PointSchemaIndex.of(cat))
        );
    }

    public static String buildCategoryPath(DeviceCategory entity) {
        List<String> path = new ArrayList<>();
        DeviceCategory current = entity;
        while (current != null) {
            path.add(current.getCategoryName());
            current = current.getParent();
        }
        return String.join(" > ", path.reversed());
    }
}
