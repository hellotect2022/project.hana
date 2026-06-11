package com.mpole.hana.digitaltwin.systemMenu.dto;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystem;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystemCategory;

import java.util.List;

public record DeviceSystemResponse(
        Long systemId,
        String systemName,
        String systemCode,
        Integer sortOrder,
        Boolean active,
        //List<Long> categoryIds,
        List<DeviceSystemCategoryBrief> categories
) {
    public static DeviceSystemResponse from(DeviceSystem system) {
        List<DeviceSystemCategory> mappings = system.getCategoryMappings();
//        List<Long> categoryIds = mappings.stream()
//                .map(m -> m.getCategory().getCategoryId())
//                .toList();
        List<DeviceSystemCategoryBrief> categories = mappings.stream()
                .map(m -> toBrief(m.getCategory()))
                .toList();
        return new DeviceSystemResponse(
                system.getSystemId(),
                system.getSystemName(),
                system.getSystemCode(),
                system.getSortOrder(),
                system.getActive(),
                //categoryIds,
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
