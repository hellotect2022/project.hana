package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.digitaltwin.device.dto.DeviceCategoryRequest;
import com.mpole.hana.digitaltwin.device.dto.DeviceCategoryResponse;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.repository.DeviceCategoryRepository;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceCategoryService {
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DevicePointSchemaSyncService devicePointSchemaSyncService;
    private final DeviceCategoryCascadeService deviceCategoryCascadeService;

    @Transactional(readOnly = true)
    public List<DeviceCategoryResponse> getCategoryInfoTree() {
        List<DeviceCategory> categories = deviceCategoryRepository.findAllWithParent();

        Map<Long, DeviceCategoryResponse> map = new HashMap<>();
        List<DeviceCategoryResponse> roots = new ArrayList<>();

        for (DeviceCategory deviceCategory : categories) {
            DeviceCategoryResponse dto = DeviceCategoryResponse.from(deviceCategory);
            map.put(dto.categoryId(), dto);
        }

        map.forEach((key, value) -> {
            if (value.isRoot()) {
                roots.add(value);
            } else {
                DeviceCategoryResponse parent = map.get(value.parentId());
                if (parent != null) parent.children().add(value);
            }
        });

        return roots;
    }

    @Transactional
    public DeviceCategoryResponse createCategory(DeviceCategoryRequest request) {
        int depth = 0;
        DeviceCategory parent = null;

        if (request.parentId() != null) {
            parent = deviceCategoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new RuntimeException("부모 카테고리를 찾을 수 없습니다. id=" + request.parentId()));
            depth = parent.getDepth() + 1;
            if (parent.isLeaf()) {
                parent.setLeaf(false);
            }
        }

        DeviceCategory category = DeviceCategory.builder()
                .categoryName(request.categoryName())
                .categoryNameEn(normalizeCategoryNameEn(request.categoryNameEn()))
                .categoryCode(requireCategoryCode(request.categoryCode()))
                .parent(parent)
                .depth(depth)
                .description(request.description())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .enabled(request.active() == null || request.active())
                .isRoot(parent == null)
                .isLeaf(true)
                .build();

        return DeviceCategoryResponse.from(deviceCategoryRepository.save(category));
    }

    @Transactional
    public DeviceCategoryResponse updateCategory(Long categoryId, DeviceCategoryRequest request) {
        DeviceCategory category = deviceCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("카테고리를 찾을 수 없습니다. id=" + categoryId));

        String previousCategoryCode = category.getCategoryCode();

        category.setCategoryName(request.categoryName());
        category.setCategoryNameEn(normalizeCategoryNameEn(request.categoryNameEn()));
        category.setCategoryCode(requireCategoryCode(request.categoryCode()));
        if (request.description() != null) {
            category.setDescription(request.description());
        }
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            category.setEnabled(request.active());
        }

        deviceCategoryRepository.save(category);
        deviceCategoryCascadeService.syncDevicesAfterCategoryUpdate(categoryId, previousCategoryCode);

        return DeviceCategoryResponse.from(category);
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        deviceCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("카테고리를 찾을 수 없습니다. id=" + categoryId));

        deviceCategoryCascadeService.deleteCategorySubtree(categoryId);
    }

    @Transactional
    public DeviceCategoryResponse updateSchema(Long categoryId, List<PointSchemaDefinition> schema) {
        DeviceCategory category = deviceCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("카테고리를 찾을 수 없습니다. id=" + categoryId));

        List<PointSchemaDefinition> oldSchema = category.getSchemaDefinitions() != null
                ? new ArrayList<>(category.getSchemaDefinitions())
                : new ArrayList<>();
        List<PointSchemaDefinition> newSchema = schema != null ? schema : new ArrayList<>();

        devicePointSchemaSyncService.syncForCategory(categoryId, oldSchema, newSchema);

        category.setSchemaDefinitions(newSchema);
        return DeviceCategoryResponse.from(category);
    }

    private static String requireCategoryCode(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new IllegalArgumentException("코드명은 필수입니다.");
        }
        return categoryCode.trim();
    }

    private static String normalizeCategoryNameEn(String categoryNameEn) {
        if (categoryNameEn == null) {
            return null;
        }
        String trimmed = categoryNameEn.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
