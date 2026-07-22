package com.mpole.hana.digitaltwin.systemMenu.dto;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSubSystem;

import java.util.List;

public record SubSystemResponse(
        Long subSystemId,
        Long systemId,
        String subSystemName,
        Integer sortOrder,
        Boolean active,
        List<DeviceSystemCategoryBrief> categories
) {
    public static SubSystemResponse from(DeviceSubSystem subSystem) {
        List<DeviceSystemCategoryBrief> categories = subSystem.getCategoryMappings().stream()
                .map(m -> toBrief(m.getCategory()))
                .toList();
        return new SubSystemResponse(
                subSystem.getSubSystemId(),
                subSystem.getDeviceSystem() != null ? subSystem.getDeviceSystem().getSystemId() : null,
                subSystem.getSubSystemName(),
                subSystem.getSortOrder(),
                subSystem.getActive(),
                categories
        );
    }

    private static DeviceSystemCategoryBrief toBrief(DeviceCategory category) {
        return new DeviceSystemCategoryBrief(
                category.getCategoryId(),
                category.getCategoryName(),
                category.getCategoryCode()
        );
    }
}
