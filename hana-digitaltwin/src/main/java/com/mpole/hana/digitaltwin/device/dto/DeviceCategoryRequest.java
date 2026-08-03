package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.property.DevicePropertyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceCategoryRequest(
        @NotBlank @Size(max = 200) String categoryName,
        @Size(max = 200) String categoryNameEn,
        @NotBlank @Size(max = 200) String categoryCode,
        Long parentId,
        String description,
        Integer displayOrder,
        Boolean active,
        Long assetId,
        DevicePropertyType propertyType   // 이 카테고리 장비가 쓰는 property_info 타입(없으면 null)
) {
}
