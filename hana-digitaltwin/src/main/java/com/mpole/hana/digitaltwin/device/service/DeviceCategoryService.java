package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.digitaltwin.device.dto.DeviceCategoryRequest;
import com.mpole.hana.digitaltwin.device.dto.DeviceCategoryResponse;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.repository.DeviceCategoryRepository;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import com.mpole.hana.digitaltwin.unityAsset.repository.UnityAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceCategoryService {
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DevicePointSchemaSyncService devicePointSchemaSyncService;
    private final DeviceCategoryCascadeService deviceCategoryCascadeService;
    private final UnityAssetRepository unityAssetRepository;

    /** assetId 로 카테고리 기본 3D 에셋을 조회한다(null 이면 해제). */
    private UnityAsset resolveDefaultAsset(Long assetId) {
        if (assetId == null) {
            return null;
        }
        return unityAssetRepository.findById(assetId)
                .orElseThrow(() -> new RuntimeException("3D 에셋을 찾을 수 없습니다. id=" + assetId));
    }

    /** 전체 카테고리 트리(최상위들). */
    @Transactional(readOnly = true)
    public List<DeviceCategoryResponse> getCategoryInfoTree() {
        Map<Long, DeviceCategoryResponse> indexed = buildIndexedTree();
        return indexed.values().stream()
                .filter(DeviceCategoryResponse::isRoot)
                .sorted(TREE_ORDER)
                .toList();
    }

    /**
     * 특정 카테고리 <b>하나와 그 하위 트리</b>.
     *
     * <p>응답이 {@code List} 인 것은 전체 조회와 형태를 맞추기 위함이다 — 원소는 항상 1개다.
     * 그래야 클라이언트가 두 API 를 같은 렌더 코드로 처리할 수 있다.</p>
     *
     * @throws IllegalArgumentException 그런 카테고리가 없을 때
     */
    @Transactional(readOnly = true)
    public List<DeviceCategoryResponse> getCategoryInfoTree(Long categoryId) {
        DeviceCategoryResponse target = buildIndexedTree().get(categoryId);
        if (target == null) {
            throw new IllegalArgumentException("카테고리를 찾을 수 없습니다. id=" + categoryId);
        }
        return List.of(target);
    }

    /**
     * 전 카테고리를 한 번 읽어 부모-자식을 이어 붙이고 {@code categoryId} 로 색인해 돌려준다.
     *
     * <p>전체 조회든 단건 조회든 <b>트리를 통째로 만든 뒤 시작점만 다르게</b> 잡는다.
     * 카테고리는 200개 미만이라 재귀 조회를 반복하는 것보다 한 번에 읽는 편이 싸다.</p>
     */
    private Map<Long, DeviceCategoryResponse> buildIndexedTree() {
        List<DeviceCategory> categories = deviceCategoryRepository.findAllWithParent();

        Map<Long, DeviceCategoryResponse> map = new HashMap<>();
        for (DeviceCategory deviceCategory : categories) {
            DeviceCategoryResponse dto = DeviceCategoryResponse.from(deviceCategory);
            map.put(dto.categoryId(), dto);
        }

        for (DeviceCategoryResponse value : map.values()) {
            if (value.isRoot()) {
                continue;
            }
            DeviceCategoryResponse parent = map.get(value.parentId());
            if (parent != null) {
                parent.children().add(value);
            }
        }

        // HashMap 순회 순서는 보장되지 않는다 — 정렬하지 않으면 호출할 때마다 순서가 달라진다.
        map.values().forEach(v -> v.children().sort(TREE_ORDER));

        return map;
    }

    /** 표시 순서 → 이름. displayOrder 가 비어 있어도 순서가 흔들리지 않게 이름으로 받친다. */
    private static final Comparator<DeviceCategoryResponse> TREE_ORDER = Comparator
            .comparing(DeviceCategoryResponse::displayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DeviceCategoryResponse::categoryName, Comparator.nullsLast(Comparator.naturalOrder()));

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
                .defaultAsset(resolveDefaultAsset(request.assetId()))
                .propertyType(request.propertyType())
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

        category.setDefaultAsset(resolveDefaultAsset(request.assetId()));
        category.setPropertyType(request.propertyType());   // 없으면 null 로 갱신(해제 가능)

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
        DeviceCategory category = deviceCategoryRepository.findWithParentJustThreeDepthById(categoryId)
                .orElseThrow(() -> new RuntimeException("카테고리를 찾을 수 없습니다. id=" + categoryId));

        List<PointSchemaDefinition> oldSchema = category.getSchemaDefinitions() != null
                ? new ArrayList<>(category.getSchemaDefinitions())
                : new ArrayList<>();
        List<PointSchemaDefinition> newSchema = schema != null ? schema : new ArrayList<>();

        devicePointSchemaSyncService.syncForCategory(categoryId, oldSchema, newSchema);


        // 2. 주소값을 갈아끼우지 않고 내부 값만 dirty checking 하도록 유도
        category.setSchemaDefinitions(schema);
        deviceCategoryRepository.saveAndFlush(category);

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
