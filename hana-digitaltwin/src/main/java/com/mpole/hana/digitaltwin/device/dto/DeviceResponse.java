package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.entity.DevicePlacement;
import com.mpole.hana.digitaltwin.realtime.dto.DeviceRealtimeResponse;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import com.mpole.hana.digitaltwin.device.property.DevicePropertyInfo;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public record DeviceResponse(
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
        DeviceRealtimeResponse currentValue
) {
    public static DeviceResponse from(Device d) {
        if (d==null) return null;
        DeviceCategory cat = d.getCategory();
        UnityAsset asset = d.getUnityAsset();
        DevicePlacement p = d.getPlacement();
        DeviceCurrentValue c = d.getDeviceCurrentValue();

        return new DeviceResponse(
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
                , DeviceRealtimeResponse.from(c)
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
