package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategoryDepth;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.device.repository.DeviceCategoryRepository;
import com.mpole.hana.digitaltwin.device.repository.DeviceCctvMappingRepository;
import com.mpole.hana.digitaltwin.device.repository.DevicePointRepository;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.device.util.DeviceKeyUtils;
import com.mpole.hana.digitaltwin.event.repository.EventRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceCategoryCascadeService {

    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceRepository deviceRepository;
    private final DevicePointRepository devicePointRepository;
    private final DeviceCctvMappingRepository deviceCctvMappingRepository;
    private final EventRuleRepository eventRuleRepository;

    /**
     * 카테고리 코드명 변경 시, 하위 소분류에 연결된 장비·포인트 키 재생성
     */
    @Transactional
    public void syncDevicesAfterCategoryUpdate(Long categoryId, String previousCategoryCode) {
        DeviceCategory updated = deviceCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("카테고리를 찾을 수 없습니다. id=" + categoryId));

        if (Objects.equals(trimToNull(previousCategoryCode), trimToNull(updated.getCategoryCode()))) {
            return;
        }

        Map<Long, DeviceCategory> categoryById = loadCategoryMap();
        Set<Long> subtreeIds = collectSubtreeCategoryIds(categoryId, categoryById);

        // 상위 카테고리를 수정하면 그 밑은 모든 하위 카테고리들 id 를 수집해야함
        List<Long> smallCategoryIds = collectSmallCategoryIds(subtreeIds, categoryById);
        if (smallCategoryIds.isEmpty()) {
            return;
        }

        List<Device> devices = deviceRepository.findByCategory_CategoryIdIn(smallCategoryIds);
        if (devices.isEmpty()) {
            return;
        }
        // 중복되는 device_key 값 여부 검증
        assertNoDeviceKeyConflicts(devices, categoryById);

        List<Long> deviceIds = devices.stream().map(Device::getDeviceId).toList();
        List<DevicePoint> points = devicePointRepository.findAllByDevice_DeviceIdIn(deviceIds);
        // 중복되는 point_key 값 여부 검증
        assertNoPointKeyConflicts(devices, points, categoryById);

        deviceRepository.bulkRebuildDeviceKeysForSmallCategoriesIn(smallCategoryIds);

        // device 랑 조인해서 device_key 값 기준으로 tag_name 을 붙여서 업데이트 함
        devicePointRepository.bulkRebuildPointKeysForDevicesInSmallCategories(smallCategoryIds);
    }

    /**
     * 카테고리(및 하위 카테고리) 삭제 전 연결 장비·포인트·관련 데이터 제거
     */
    @Transactional
    public void deleteDevicesForCategorySubtree(Long rootCategoryId) {
        Map<Long, DeviceCategory> categoryById = loadCategoryMap();
        Set<Long> subtreeIds = collectSubtreeCategoryIds(rootCategoryId, categoryById);

        List<Device> devices = deviceRepository.findByCategory_CategoryIdIn(subtreeIds);
        if (devices.isEmpty()) {
            return;
        }

        List<Long> deviceIds = devices.stream().map(Device::getDeviceId).toList();

        eventRuleRepository.deleteByDeviceIdIn(deviceIds);
        deviceCctvMappingRepository.deleteByDeviceIdIn(deviceIds);
        devicePointRepository.deleteByDevice_DeviceIdIn(deviceIds);
        deviceRepository.deleteAll(devices);
    }

    /**
     * 단일 장비 삭제 (이벤트 규칙·CCTV 매핑·포인트·배치 포함)
     */
    @Transactional
    public void deleteSingleDevice(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("장비를 찾을 수 없습니다. id=" + deviceId));

        eventRuleRepository.deleteByDeviceId(deviceId);
        deviceCctvMappingRepository.deleteByDeviceId(deviceId);
        device.clearPlacement();
        devicePointRepository.deleteByDevice_DeviceIdIn(List.of(deviceId));
        deviceRepository.delete(device);
    }

    /**
     * 목록 필터용 — 선택 카테고리(대·중·소) 하위 소분류 ID 목록
     * @return null이면 필터 없음, 빈 리스트면 매칭 없음
     */
    @Transactional(readOnly = true)
    public List<Long> resolveSmallCategoryIdsForFilter(Long rootCategoryId) {
        if (rootCategoryId == null) {
            return null;
        }
        Map<Long, DeviceCategory> categoryById = loadCategoryMap();
        if (!categoryById.containsKey(rootCategoryId)) {
            return List.of();
        }
        Set<Long> subtreeIds = collectSubtreeCategoryIds(rootCategoryId, categoryById);
        return collectSmallCategoryIds(subtreeIds, categoryById);
    }

    /**
     * 장비 카테고리·이름 변경 후 device_key 및 연결 point_key 일괄 재생성
     */
    @Transactional
    public void syncSingleDeviceKeys(Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("장비를 찾을 수 없습니다. id=" + deviceId));
        if (device.getCategory() == null) {
            return;
        }

        DeviceCategory small = deviceCategoryRepository.findWithParentJustThreeDepthById(
                        device.getCategory().getCategoryId())
                .orElseThrow(() -> new IllegalStateException("device_key를 생성할 수 없습니다."));
        DeviceCategory mid = small.getParent();
        DeviceCategory major = mid != null ? mid.getParent() : null;

        String newDeviceKey = DeviceKeyUtils.buildDeviceKey(major, mid, small, device.getDeviceName());
        if (newDeviceKey.equals(device.getDeviceKey())) {
            return;
        }

        if (deviceRepository.existsByDeviceKeyAndDeviceIdNot(newDeviceKey, deviceId)) {
            throw new IllegalStateException("이미 사용 중인 device_key 입니다: " + newDeviceKey);
        }

        List<DevicePoint> points = devicePointRepository.findAllByDevice_DeviceId(deviceId);
        for (DevicePoint point : points) {
            String tagName = point.getTagName();
            if (tagName == null || tagName.isBlank()) {
                continue;
            }
            String newPointKey = DeviceKeyUtils.buildPointKey(newDeviceKey, tagName);
            if (!newPointKey.equals(point.getPointKey())
                    && devicePointRepository.existsByPointKeyAndPointIdNot(newPointKey, point.getPointId())) {
                throw new IllegalStateException("이미 사용 중인 point_key 입니다: " + newPointKey);
            }
        }

        device.setDeviceKey(newDeviceKey);
        for (DevicePoint point : points) {
            String tagName = point.getTagName();
            if (tagName == null || tagName.isBlank()) {
                continue;
            }
            point.setPointKey(DeviceKeyUtils.buildPointKey(newDeviceKey, tagName));
        }
        deviceRepository.save(device);
        if (!points.isEmpty()) {
            devicePointRepository.saveAll(points);
        }
    }

    /**
     * 하위 카테고리를 depth 내림차순으로 삭제 (leaf → root)
     */
    @Transactional
    public void deleteCategorySubtree(Long rootCategoryId) {
        deleteDevicesForCategorySubtree(rootCategoryId);

        Map<Long, DeviceCategory> categoryById = loadCategoryMap();
        Set<Long> subtreeIds = collectSubtreeCategoryIds(rootCategoryId, categoryById);

        List<DeviceCategory> toDelete = subtreeIds.stream()
                .map(categoryById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DeviceCategory::getDepth).reversed())
                .toList();

        DeviceCategory root = categoryById.get(rootCategoryId);
        Long parentId = root != null && root.getParent() != null ? root.getParent().getCategoryId() : null;

        DeviceCategory parent = root.getParent();
        if (parent != null) {
            // 부모 컬렉션에서 빼면 orphanRemoval 이 root + 하위 서브트리를 통째로 삭제
            parent.getChildren().remove(root);
            if (parent.getChildren().isEmpty()) {
                parent.setLeaf(true);
            }
        } else {
            // 최상위면 직접 삭제 (cascade 로 서브트리 삭제)
            deviceCategoryRepository.delete(root);
        }
//        deviceCategoryRepository.deleteAll(toDelete);
//
//        if (parentId != null) {
//            deviceCategoryRepository.findById(parentId).ifPresent(parent -> {
//                if (!deviceCategoryRepository.existsByParent(parent)) {
//                    parent.setLeaf(true);
//                }
//            });
//        }
    }

    private void assertNoDeviceKeyConflicts(List<Device> devices, Map<Long, DeviceCategory> categoryById) {
        List<Long> allDeviceIds = devices.stream().map(Device::getDeviceId).toList();
        List<String> candidateKeys = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (Device device : devices) {
            String newDeviceKey = computeDeviceKey(device, categoryById);
            String currentDeviceKey = device.getDeviceKey();
            if (newDeviceKey.equals(currentDeviceKey)) {
                continue;
            }
            if (!seenKeys.add(newDeviceKey)) {
                throw new IllegalStateException("이미 사용 중인 device_key 입니다: " + newDeviceKey);
            }
            candidateKeys.add(newDeviceKey);
        }

        if (candidateKeys.isEmpty()) {
            return;
        }

        List<String> conflicts = deviceRepository.findConflictingDeviceKeys(candidateKeys, allDeviceIds);
        if (!conflicts.isEmpty()) {
            throw new IllegalStateException("이미 사용 중인 device_key 입니다: " + conflicts.get(0));
        }
    }

    private void assertNoPointKeyConflicts(
            List<Device> devices,
            List<DevicePoint> points,
            Map<Long, DeviceCategory> categoryById
    ) {
        Map<Long, String> newDeviceKeyById = devices.stream()
                .collect(Collectors.toMap(Device::getDeviceId, d -> computeDeviceKey(d, categoryById)));

        List<Long> allPointIds = points.stream().map(DevicePoint::getPointId).toList();
        List<String> candidateKeys = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (DevicePoint point : points) {
            String tagName = point.getTagName();
            if (tagName == null || tagName.isBlank()) {
                continue;
            }
            Long deviceId = point.getDevice().getDeviceId();
            String newDeviceKey = newDeviceKeyById.get(deviceId);
            if (newDeviceKey == null) {
                continue;
            }
            String newPointKey = DeviceKeyUtils.buildPointKey(newDeviceKey, tagName);
            if (newPointKey.equals(point.getPointKey())) {
                continue;
            }
            if (!seenKeys.add(newPointKey)) {
                throw new IllegalStateException(
                        "이미 사용 중인 point_key 입니다: " + newPointKey + " (deviceId=" + deviceId + ")"
                );
            }
            candidateKeys.add(newPointKey);
        }

        if (candidateKeys.isEmpty()) {
            return;
        }

//        List<String> conflicts = devicePointRepository.findConflictingPointKeys(candidateKeys, allPointIds);
//        if (!conflicts.isEmpty()) {
//            throw new IllegalStateException("이미 사용 중인 point_key 입니다: " + conflicts.get(0));
//        }
    }

    private static String computeDeviceKey(Device device, Map<Long, DeviceCategory> categoryById) {
        if (device.getCategory() == null) {
            throw new IllegalStateException("device_key를 생성할 수 없습니다.");
        }
        DeviceCategory small = categoryById.get(device.getCategory().getCategoryId());
        if (small == null || small.getDepth() == null || small.getDepth() != DeviceCategoryDepth.MAX_DEPTH) {
            throw new IllegalStateException("device_key를 생성할 수 없습니다.");
        }
        DeviceCategory mid = small.getParent() != null
                ? categoryById.get(small.getParent().getCategoryId())
                : null;
        DeviceCategory major = mid != null && mid.getParent() != null
                ? categoryById.get(mid.getParent().getCategoryId())
                : null;
        return DeviceKeyUtils.buildDeviceKey(major, mid, small, device.getDeviceName());
    }

    private static List<Long> collectSmallCategoryIds(
            Set<Long> subtreeIds,
            Map<Long, DeviceCategory> categoryById
    ) {
        return subtreeIds.stream()
                .map(categoryById::get)
                .filter(c -> c != null
                        && c.getDepth() != null
                        && c.getDepth() == DeviceCategoryDepth.MAX_DEPTH)
                .map(DeviceCategory::getCategoryId)
                .toList();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<Long, DeviceCategory> loadCategoryMap() {
        Map<Long, DeviceCategory> map = new HashMap<>();
        for (DeviceCategory category : deviceCategoryRepository.findAllWithParent()) {
            map.put(category.getCategoryId(), category);
        }
        return map;
    }

    private Set<Long> collectSubtreeCategoryIds(Long rootId, Map<Long, DeviceCategory> categoryById) {
        Map<Long, List<Long>> childrenByParent = new HashMap<>();
        for (DeviceCategory category : categoryById.values()) {
            Long parentId = category.getParent() != null ? category.getParent().getCategoryId() : null;
            if (parentId != null) {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(category.getCategoryId());
            }
        }

        Set<Long> result = new HashSet<>();
        collectDescendants(rootId, childrenByParent, result);
        return result;
    }

    private void collectDescendants(Long id, Map<Long, List<Long>> childrenByParent, Set<Long> result) {
        if (id == null || !result.add(id)) {
            return;
        }
        List<Long> children = childrenByParent.getOrDefault(id, List.of());
        for (Long childId : children) {
            collectDescendants(childId, childrenByParent, result);
        }
    }
}
