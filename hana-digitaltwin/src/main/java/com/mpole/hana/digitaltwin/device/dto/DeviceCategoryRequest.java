package com.mpole.hana.digitaltwin.device.dto;

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
        Long assetId
) {
}
