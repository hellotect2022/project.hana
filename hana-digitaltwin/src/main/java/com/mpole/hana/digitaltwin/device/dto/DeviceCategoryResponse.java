package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

@Builder
public record DeviceCategoryResponse(
        Long categoryId,
        Long parentId,
        Integer depth,
        String categoryCode,
        String categoryName,
        String categoryNameEn,
        String description,
        Integer displayOrder,
        Boolean active,
        String fullPath,
        Boolean isRoot,
        Boolean isLeaf,
        List<PointSchemaDefinition> schemaDefinitions,
        List<DeviceCategoryResponse> children
) {
    public static DeviceCategoryResponse from(DeviceCategory entity) {
        return DeviceCategoryResponse.builder()
                .categoryId(entity.getCategoryId())
                .parentId(entity.getParent() != null ? entity.getParent().getCategoryId() : null)
                .depth(entity.getDepth())
                .categoryName(entity.getCategoryName())
                .categoryNameEn(entity.getCategoryNameEn())
                .categoryCode(entity.getCategoryCode())
                .description(entity.getDescription())
                .displayOrder(entity.getDisplayOrder())
                .active(entity.isEnabled())
                .fullPath(buildCategoryPath(entity))
                .isRoot(entity.isRoot())
                .isLeaf(entity.isLeaf())
                .schemaDefinitions(entity.getSchemaDefinitions() != null ? entity.getSchemaDefinitions() : new ArrayList<>())
                .children(new ArrayList<>())
                .build();
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
