package com.mpole.hana.digitaltwin.systemMenu.service;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.repository.DeviceCategoryRepository;
import com.mpole.hana.digitaltwin.systemMenu.dto.SubSystemCreateRequest;
import com.mpole.hana.digitaltwin.systemMenu.dto.SubSystemResponse;
import com.mpole.hana.digitaltwin.systemMenu.dto.SubSystemSortOrderRequest;
import com.mpole.hana.digitaltwin.systemMenu.dto.SubSystemUpdateRequest;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSubSystem;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystem;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystemCategory;
import com.mpole.hana.digitaltwin.systemMenu.repository.DeviceSubSystemRepository;
import com.mpole.hana.digitaltwin.systemMenu.repository.DeviceSystemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubSystemService {

    private final DeviceSubSystemRepository deviceSubSystemRepository;
    private final DeviceSystemRepository deviceSystemRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;

    @Transactional(readOnly = true)
    public List<SubSystemResponse> getSubSystemsBySystemId(Long systemId) {
        return deviceSubSystemRepository.findByDeviceSystem_SystemIdOrderBySortOrderAsc(systemId)
                .stream()
                .map(SubSystemResponse::from)
                .toList();
    }

    @Transactional
    public void createSubSystem(SubSystemCreateRequest request) {
        DeviceSystem system = deviceSystemRepository.findById(request.systemId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "해당 BMS 시스템이 존재하지 않습니다. id=" + request.systemId()));

        DeviceSubSystem subSystem = DeviceSubSystem.create(
                system, request.subSystemName(), request.sortOrder(), request.active());
        applyCategoryMappings(subSystem, request.categoryIds());
        deviceSubSystemRepository.save(subSystem);
    }

    @Transactional
    public void updateSubSystem(Long subSystemId, SubSystemUpdateRequest request) {
        DeviceSubSystem subSystem = getSubSystemEntity(subSystemId);
        subSystem.update(request.subSystemName(), request.sortOrder(), request.active());
        applyCategoryMappings(subSystem, request.categoryIds());
    }

    @Transactional
    public void updateSubSystemSortOrders(List<SubSystemSortOrderRequest> requests) {
        for (SubSystemSortOrderRequest request : requests) {
            DeviceSubSystem subSystem = deviceSubSystemRepository.findById(request.subSystemId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "해당 하위 시스템이 존재하지 않습니다. id=" + request.subSystemId()));
            subSystem.changeSortOrder(request.sortOrder());
        }
    }

    @Transactional
    public void deleteSubSystem(Long subSystemId) {
        if (!deviceSubSystemRepository.existsById(subSystemId)) {
            throw new EntityNotFoundException("해당 하위 시스템이 존재하지 않습니다. id=" + subSystemId);
        }
        deviceSubSystemRepository.deleteById(subSystemId);
    }

    /**
     * subSystem 기준 소분류 카테고리 매핑을 목표 목록과 일치시킨다.
     * (기존 DeviceSystemService.applyCategoryMappings 로직을 subSystem 레이어로 이관)
     */
    private void applyCategoryMappings(DeviceSubSystem subSystem, List<Long> categoryIds) {
        List<Long> targetIds = (categoryIds == null) ? List.of() : categoryIds;

        // 1) 기존 매핑 중 새 목록에 없는 것만 제거 (orphanRemoval → DELETE)
        subSystem.getCategoryMappings()
                .removeIf(mapping -> !targetIds.contains(mapping.getCategory().getCategoryId()));

        if (targetIds.isEmpty()) {
            return;
        }

        // 2) 이미 매핑된 categoryId는 그대로 두고(중복 INSERT 방지), 새로 추가된 것만 INSERT
        Set<Long> existingIds = subSystem.getCategoryMappings().stream()
                .map(mapping -> mapping.getCategory().getCategoryId())
                .collect(Collectors.toSet());

        for (Long categoryId : targetIds) {
            if (existingIds.contains(categoryId)) {
                continue; // 기존에 있던 항목은 제외
            }
            DeviceCategory category = deviceCategoryRepository.findById(categoryId)
                    .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다. id=" + categoryId));
            if (!category.isSmallCategory()) {
                throw new IllegalStateException("소분류(depth=2) 카테고리만 매핑할 수 있습니다. id=" + categoryId);
            }
            subSystem.addCategoryMapping(DeviceSystemCategory.of(subSystem, category));
        }
    }

    private DeviceSubSystem getSubSystemEntity(Long subSystemId) {
        return deviceSubSystemRepository.findByIdWithCategories(subSystemId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "해당 하위 시스템이 존재하지 않습니다. id=" + subSystemId));
    }
}
