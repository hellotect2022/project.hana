package com.mpole.hana.digitaltwin.systemMenu.service;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.repository.DeviceCategoryRepository;
import com.mpole.hana.digitaltwin.iam.role.entity.Role;
import com.mpole.hana.digitaltwin.systemMenu.dto.*;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystem;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystemCategory;
import com.mpole.hana.digitaltwin.systemMenu.entity.RoleDeviceSystem;
import com.mpole.hana.digitaltwin.systemMenu.repository.DeviceSystemRepository;
import com.mpole.hana.digitaltwin.systemMenu.repository.RoleDeviceSystemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceSystemService {

    private final DeviceSystemRepository deviceSystemRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final RoleDeviceSystemRepository roleDeviceSystemRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<DeviceSystemResponse> getDeviceSystems() {
        return deviceSystemRepository.findAllWithCategories()
                .stream()
                .map(DeviceSystemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> getDeviceSystemPermissions(Long roleId) {
        return roleDeviceSystemRepository.findSystemIdsByRoleId(roleId);
    }

    @Transactional
    public void createDeviceSystem(DeviceSystemCreateRequest request) {
        if (deviceSystemRepository.existsBySystemName(request.systemName())) {
            throw new IllegalStateException("이미 존재하는 시스템명입니다.");
        }
        if (deviceSystemRepository.existsBySystemCode(request.systemCode())) {
            throw new IllegalStateException("이미 존재하는 시스템 코드입니다.");
        }
        DeviceSystem system = DeviceSystem.create(request);
        applyCategoryMappings(system, request.categoryIds());
        deviceSystemRepository.save(system);
    }

    @Transactional
    public void updateDeviceSystem(Long systemId, DeviceSystemUpdateRequest request) {
        DeviceSystem system = getDeviceSystemEntity(systemId);
        if (!system.getSystemName().equals(request.systemName())
                && deviceSystemRepository.existsBySystemName(request.systemName())) {
            throw new IllegalStateException("이미 존재하는 시스템명입니다.");
        }
        if (!system.getSystemCode().equals(request.systemCode())
                && deviceSystemRepository.existsBySystemCode(request.systemCode())) {
            throw new IllegalStateException("이미 존재하는 시스템 코드입니다.");
        }
        system.update(request);
        applyCategoryMappings(system, request.categoryIds());
    }

    @Transactional
    public void updateDeviceSystemSortOrders(List<DeviceSystemSortOrderRequest> requests) {
        for (DeviceSystemSortOrderRequest request : requests) {
            DeviceSystem system = getDeviceSystemEntity(request.systemId());
            system.changeSortOrder(request.sortOrder());
        }
    }

    @Transactional
    public void deleteDeviceSystem(Long systemId) {
        if (!deviceSystemRepository.existsById(systemId)) {
            throw new EntityNotFoundException("해당 BMS 시스템이 존재하지 않습니다.");
        }
        roleDeviceSystemRepository.deleteByDeviceSystem_SystemId(systemId);
        deviceSystemRepository.deleteById(systemId);
    }

    @Transactional
    public void updateDeviceSystemPermissions(Long roleId, DeviceSystemPermissionRequest request) {
        roleDeviceSystemRepository.deleteByRole_RoleId(roleId);
        Role role = entityManager.getReference(Role.class, roleId);
        List<RoleDeviceSystem> mappings = request.systemIds()
                .stream()
                .map(systemId -> {
                    DeviceSystem system = entityManager.getReference(DeviceSystem.class, systemId);
                    return RoleDeviceSystem.of(role, system);
                })
                .toList();
        roleDeviceSystemRepository.saveAll(mappings);
    }

    private void applyCategoryMappings(DeviceSystem system, List<Long> categoryIds) {
        system.getCategoryMappings().clear();
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        List<DeviceSystemCategory> mappings = new ArrayList<>();
        for (Long categoryId : categoryIds) {
            DeviceCategory category = deviceCategoryRepository.findById(categoryId)
                    .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다. id=" + categoryId));
            if (!category.isSmallCategory()) {
                throw new IllegalStateException("소분류(depth=2) 카테고리만 매핑할 수 있습니다. id=" + categoryId);
            }
            mappings.add(DeviceSystemCategory.of(system, category));
        }
        system.getCategoryMappings().addAll(mappings);
    }

    private DeviceSystem getDeviceSystemEntity(Long systemId) {
        return deviceSystemRepository.findByIdWithCategories(systemId)
                .orElseThrow(() -> new EntityNotFoundException("해당 BMS 시스템이 존재하지 않습니다."));
    }
}
