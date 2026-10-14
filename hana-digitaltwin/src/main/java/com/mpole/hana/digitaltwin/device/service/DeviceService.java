package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.digitaltwin.command.repository.CommandPointRepository;
import com.mpole.hana.digitaltwin.device.dto.BulkActiveRequest;
import com.mpole.hana.digitaltwin.device.dto.CctvInfoProjection;
import com.mpole.hana.digitaltwin.device.dto.DevicePointMappingItemResponse;
import com.mpole.hana.digitaltwin.device.dto.DevicePointMappingPageResponse;
import com.mpole.hana.digitaltwin.device.dto.DevicePointRefMappingRequest;
import com.mpole.hana.digitaltwin.device.dto.DevicePointRegisterItem;
import com.mpole.hana.digitaltwin.device.dto.DevicePointResponse;
import com.mpole.hana.digitaltwin.device.dto.DeviceResponse;
import com.mpole.hana.digitaltwin.device.dto.DeviceUpdateRequest;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategoryDepth;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.device.repository.DeviceCategoryRepository;
import com.mpole.hana.digitaltwin.device.repository.DeviceCctvMappingRepository;
import com.mpole.hana.digitaltwin.device.repository.DevicePointRepository;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSubSystem;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystem;
import com.mpole.hana.digitaltwin.systemMenu.repository.DeviceSubSystemRepository;
import com.mpole.hana.digitaltwin.systemMenu.repository.DeviceSystemRepository;
import com.mpole.hana.digitaltwin.realtime.api.DeviceRealtimeController;
import jakarta.persistence.EntityNotFoundException; 
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceCctvMappingRepository deviceCctvMappingRepository;
    private final DevicePointRepository devicePointRepository;
    private final DeviceCategoryCascadeService deviceCategoryCascadeService;
    private final DeviceSystemRepository deviceSystemRepository;
    private final DeviceSubSystemRepository deviceSubSystemRepository;
    private final CommandPointRepository commandPointRepository;

    @Transactional
    public DeviceResponse saveDevice(DeviceUpdateRequest request) {
        if (request.deviceKey() != null && deviceRepository.existsByDeviceKey(request.deviceKey())) {
            throw new IllegalStateException("이미 사용 중인 deviceKey 입니다: " + request.deviceKey());
        }

        Device device = Device.builder()
                .deviceName(request.deviceName())
                .deviceKey(request.deviceKey())
                .active(request.active())
                .description(request.description())
                .build();

        // 3D 에셋은 더 이상 디바이스가 아닌 카테고리(defaultAsset) 기준으로 렌더한다.
        // request.assetId() 는 하위호환을 위해 남겨두되 사용하지 않는다(deprecated).

        if (request.categoryId() != null) {
            DeviceCategory category = deviceCategoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new RuntimeException("카테고리를 찾을 수 없습니다. id=" + request.categoryId()));
            if (!category.isSmallCategory()) {
                throw new IllegalStateException("장비는 소분류(depth=2) 카테고리에만 연결할 수 있습니다.");
            }

            device.setCategory(category);
        }

        Device saved = deviceRepository.save(device);
        saveDevicePoints(saved, request.points());
        return DeviceResponse.from(deviceRepository.fetchDeviceById(saved.getDeviceId()));
    }

    private void saveDevicePoints(Device device, List<DevicePointRegisterItem> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        for (DevicePointRegisterItem item : points) {
            String tagName = resolveSchemaTagName(item);
            if (tagName.isBlank()) {
                continue;
            }
            String pointKey = resolvePointKey(item, device.getDeviceKey(), tagName);
            if (pointKey.isBlank()) {
                throw new IllegalStateException("point_key를 생성할 수 없습니다. tagName=" + tagName);
            }
            if (devicePointRepository.existsByPointKey(pointKey)) {
                throw new IllegalStateException("이미 사용 중인 point_key 입니다: " + pointKey);
            }
            String pointName = item.pointName() != null && !item.pointName().isBlank()
                    ? item.pointName().trim()
                    : tagName;

            DevicePoint point = DevicePoint.builder()
                    .device(device)
                    .pointKey(pointKey)
                    .tagName(tagName)
                    .pointName(pointName)
                    .pointType(item.pointType())
                    .unit(item.unit())
                    .active(item.isDisplay() == null || item.isDisplay())
                    .build();
            devicePointRepository.save(point);
        }
    }

    /** tbl_device_point.tag_name — 스키마 원본 tag만 (예: test3) */
    private String resolveSchemaTagName(DevicePointRegisterItem item) {
        if (item.schemaTagName() != null && !item.schemaTagName().isBlank()) {
            return item.schemaTagName().trim();
        }
        if (item.tagName() != null && !item.tagName().isBlank()) {
            return item.tagName().trim();
        }
        return "";
    }

    /** tbl_device_point.point_key — {deviceKey}_{schemaTagName} (예: 전기설비_전력제어_SUBE_test_test3) */
    private String resolvePointKey(DevicePointRegisterItem item, String deviceKey, String schemaTagName) {
        if (deviceKey != null && !deviceKey.isBlank() && !schemaTagName.isBlank()) {
            return deviceKey.trim() + "_" + schemaTagName;
        }
        if (item.pointKey() != null && !item.pointKey().isBlank()) {
            return item.pointKey().trim();
        }
        return "";
    }
    @Transactional(readOnly = true)
    public Long getAllDevicesCnt() {
        return deviceRepository.count();
    }
    @Transactional(readOnly = true)
    public List<DeviceResponse> getAllDevices() {
        return mapWithCommandPoints(deviceRepository.fetchAllDevices());
    }

    @Transactional(readOnly = true)
    public Page<DeviceResponse> getDevicesPage(String keyword, Long categoryId, Pageable pageable) {
        String kw = normalizeKeyword(keyword);
        List<Long> categoryIds = deviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(categoryId);
//        if (categoryIds != null && categoryIds.isEmpty()) {
//            return Page.empty(pageable);
//        }
        return mapWithCommandPoints(deviceRepository.searchDevices(kw, categoryIds, pageable));
    }

    @Transactional(readOnly = true)
    public DeviceResponse getTargetDevice(Long deviceId) {
        Device device = deviceRepository.fetchDeviceById(deviceId);
        return mapWithCommandPoints(device);
    }

    // region 제어 포인트(command) 임베드 — device_id 배치 1회 조회로 N+1 방지
    private Map<Long, List<DeviceResponse.CommandPointItem>> loadCommandPointItems(Collection<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Map.of();
        }
        return commandPointRepository.findByDevice_DeviceIdInAndActiveTrue(deviceIds).stream()
                .collect(Collectors.groupingBy(
                        cp -> cp.getDevice().getDeviceId(),
                        Collectors.mapping(cp -> new DeviceResponse.CommandPointItem(
                                cp.getCommandPointId(),
                                cp.getLabel(),
                                cp.getPoint() != null ? cp.getPoint().getTagName() : null,
                                cp.getOnValue(),
                                cp.getOffValue()
                        ), Collectors.toList())));
    }

    private List<DeviceResponse> mapWithCommandPoints(List<Device> devices) {
        Map<Long, List<DeviceResponse.CommandPointItem>> cmd =
                loadCommandPointItems(devices.stream().map(Device::getDeviceId).toList());
        return devices.stream()
                .map(d -> DeviceResponse.from(d, cmd.getOrDefault(d.getDeviceId(), List.of())))
                .toList();
    }

    private Page<DeviceResponse> mapWithCommandPoints(Page<Device> page) {
        Map<Long, List<DeviceResponse.CommandPointItem>> cmd =
                loadCommandPointItems(page.getContent().stream().map(Device::getDeviceId).toList());
        return page.map(d -> DeviceResponse.from(d, cmd.getOrDefault(d.getDeviceId(), List.of())));
    }

    private DeviceResponse mapWithCommandPoints(Device device) {
        if (device == null) {
            return null;
        }
        List<DeviceResponse.CommandPointItem> items =
                loadCommandPointItems(List.of(device.getDeviceId()))
                        .getOrDefault(device.getDeviceId(), List.of());
        return DeviceResponse.from(device, items);
    }
    // endregion

    @Transactional(readOnly = true)
    public List<DevicePointResponse> getDevicePoints(Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new RuntimeException("장비를 찾을 수 없습니다. id=" + deviceId);
        }
        return devicePointRepository.findAllByDevice_DeviceIdOrderByTagNameAsc(deviceId).stream()
                .map(DevicePointResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DevicePointMappingItemResponse> getDevicePointsForMapping(Long deviceId, String keyword) {
        String kw = normalizeKeyword(keyword);
        return devicePointRepository.findForRefMapping(deviceId, kw).stream()
                .map(DevicePointMappingItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DevicePointMappingPageResponse getDevicePointsForMappingPage(
            Long deviceId,
            Long categoryId,
            String keyword,
            boolean unmapped,
            Pageable pageable
    ) {
        String kw = normalizeKeyword(keyword);
        List<Long> categoryIds = deviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(categoryId);
        if (categoryIds != null && categoryIds.isEmpty()) {
            return DevicePointMappingPageResponse.of(Page.empty(pageable), 0);
        }
        Page<DevicePointMappingItemResponse> page = devicePointRepository
                .findForRefMappingPage(deviceId, categoryIds, kw, unmapped, pageable)
                .map(DevicePointMappingItemResponse::from);
        // mappedCount 는 전체 매핑 현황(필터 무관) — 요약 표시용
        long mappedCount = devicePointRepository.countMappedForRefMapping(deviceId, categoryIds, kw);
        return DevicePointMappingPageResponse.of(page, mappedCount);
    }

    private static String normalizeKeyword(String keyword) {
        return keyword != null && !keyword.isBlank() ? keyword.trim() : null;
    }

    @Transactional
    public void updateDevicePointRefMappings(List<DevicePointRefMappingRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        for (DevicePointRefMappingRequest request : requests) {
            DevicePoint point = devicePointRepository.findById(request.pointId())
                    .orElseThrow(() -> new RuntimeException("포인트를 찾을 수 없습니다. id=" + request.pointId()));

            String refDeviceCode = trimToNull(request.refDeviceCode());
            String refPointCode = trimToNull(request.refPointCode());

            if (refDeviceCode != null ^ refPointCode != null) {
                throw new IllegalArgumentException(
                        "ref_device_code와 ref_point_code는 함께 입력하거나 모두 비워야 합니다. pointId=" + request.pointId()
                );
            }

            if (refDeviceCode != null) {
                boolean duplicate = devicePointRepository
                        .findByRefDeviceCodeAndRefPointCodeAndPointIdNot(refDeviceCode, refPointCode, point.getPointId())
                        .isPresent();
                if (duplicate) {
                    throw new IllegalStateException(
                            "이미 다른 포인트에 연결된 SI 매핑입니다: " + refDeviceCode + ":" + refPointCode
                    );
                }
            }

            point.setRefDeviceCode(refDeviceCode);
            point.setRefPointCode(refPointCode);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
 
    @Transactional
    public void saveTagMappingsByDevice(Long deviceId, Map<String, String> mappings) {
        if (!deviceRepository.existsById(deviceId)) {
            throw new RuntimeException("장비를 찾을 수 없습니다. id=" + deviceId);
        }
        if (mappings == null || mappings.isEmpty()) {
            return;
        }

        Map<String, DevicePoint> byTag = devicePointRepository
                .findAllByDevice_DeviceIdOrderByTagNameAsc(deviceId)
                .stream()
                .collect(Collectors.toMap(DevicePoint::getTagName, Function.identity(), (a, b) -> a));

        List<DevicePointRefMappingRequest> updates = new ArrayList<>();
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            DevicePoint point = byTag.get(entry.getKey());
            if (point == null) {
                continue;
            }
            String raw = entry.getValue();
            String refDeviceCode = null;
            String refPointCode = null;
            if (raw != null && !raw.isBlank()) {
                String val = raw.trim();
                int colon = val.indexOf(':');
                if (colon <= 0 || colon >= val.length() - 1) {
                    throw new IllegalArgumentException(
                            "매핑 값 형식이 올바르지 않습니다 (deviceCode:pointCode): tagName=" + entry.getKey()
                    );
                }
                refDeviceCode = val.substring(0, colon).trim();
                refPointCode = val.substring(colon + 1).trim();
            }
            updates.add(new DevicePointRefMappingRequest(point.getPointId(), refDeviceCode, refPointCode));
        }
        updateDevicePointRefMappings(updates);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getSearchDevices(Long floorId, Long zoneId) {
        return mapWithCommandPoints(deviceRepository.fetchSearchDevices(floorId, zoneId));
    }

    @Transactional(readOnly = true)
    public Page<DeviceResponse> getTargetCategoryDevices(Long categoryId, Pageable pageable) {
        return mapWithCommandPoints(deviceRepository.fetchTargetCategoryDevices(categoryId,pageable));
    }

    @Transactional(readOnly = true)
    public long getTargetCategoryDevicesCnt(Long categoryId) {
        return deviceRepository.countByCategory_CategoryId(categoryId);
    }

    /**
     * BMS 시스템에 직접 매핑(deviceSubSystem==null)된 소분류 카테고리 기준 장비 목록 조회.
     * subSystem 밑 카테고리는 제외(그건 getDevicesBySubSystemId 경로).
     */
    @Transactional(readOnly = true)
    public Page<DeviceResponse> getDevicesBySystemId(Long systemId,Pageable pageable) {
        DeviceSystem system = deviceSystemRepository.findByIdWithCategories(systemId)
                .orElseThrow(() -> new EntityNotFoundException("해당 BMS 시스템이 존재하지 않습니다. id=" + systemId));

        List<Long> categoryIds = system.getCategoryMappings().stream()
                //.filter(mapping -> mapping.getDeviceSubSystem() == null)
                .map(mapping -> mapping.getCategory().getCategoryId())
                .toList();

        if (categoryIds.isEmpty()) {
            return Page.empty(pageable);
        }

        return mapWithCommandPoints(deviceRepository.fetchByCategoryIds(categoryIds,pageable));
    }

    /**
     * 하위 시스템(subSystem)에 매핑된 소분류 카테고리 기준 장비 목록 조회.
     * subSystem → categoryMappings → categoryIds → devices 경로.
     */
    @Transactional(readOnly = true)
    public Page<DeviceResponse> getDevicesBySubSystemId(Long subSystemId, Pageable pageable) {
        DeviceSubSystem subSystem = deviceSubSystemRepository.findByIdWithCategories(subSystemId)
                .orElseThrow(() -> new EntityNotFoundException("해당 하위 시스템이 존재하지 않습니다. id=" + subSystemId));

        List<Long> categoryIds = subSystem.getCategoryMappings().stream()
                .map(mapping -> mapping.getCategory().getCategoryId())
                .toList();

        if (categoryIds.isEmpty()) {
            return Page.empty(pageable);
        }

        return mapWithCommandPoints(deviceRepository.fetchByCategoryIds(categoryIds, pageable));
    }

    @Transactional
    public DeviceResponse updateDevice(Long deviceId, DeviceUpdateRequest request) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("장비를 찾을 수 없습니다. id=" + deviceId));

        Long previousCategoryId = device.getCategory() != null ? device.getCategory().getCategoryId() : null;
        String previousDeviceName = device.getDeviceName();

        if (request.deviceName() != null) {
            device.setDeviceName(request.deviceName());
        }
        if (request.description() != null) {
            device.setDescription(request.description());
        }
        if (request.active() != null) {
            device.setActive(request.active());
        }
        if (request.categoryId() != null) {
            DeviceCategory category = deviceCategoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new RuntimeException("카테고리를 찾을 수 없습니다. id=" + request.categoryId()));

            if (category.getDepth() != DeviceCategoryDepth.MAX_DEPTH) {
                throw new IllegalStateException("장비는 소분류(depth=2) 카테고리에만 연결할 수 있습니다.");
            }

            device.setCategory(category);
        }
        // 3D 에셋은 카테고리(defaultAsset) 기준으로 렌더한다.
        // request.assetId() 는 하위호환을 위해 남겨두되 사용하지 않는다(deprecated).

        boolean categoryChanged = request.categoryId() != null
                && !Objects.equals(previousCategoryId, request.categoryId());
        boolean nameChanged = request.deviceName() != null
                && !Objects.equals(previousDeviceName, device.getDeviceName());

        deviceRepository.save(device);

        if ((categoryChanged || nameChanged) && device.getCategory() != null) {
            deviceCategoryCascadeService.syncSingleDeviceKeys(deviceId);
        }

        return mapWithCommandPoints(deviceRepository.fetchDeviceById(deviceId));
    }

    /**
     * 장비 활성상태(active) 일괄 변경.
     * <p>bulk JPQL UPDATE 는 @PreUpdate 를 우회하므로 updatedAt 을 명시 세팅한다.</p>
     *
     * @return 영향받은(변경된) 장비 행 수
     */
    //region 장비 활성상태(active) 일괄 변경.
    @Transactional
    public int updateActiveBulk(BulkActiveRequest request) {
        if (request == null || request.scope() == null) {
            throw new IllegalArgumentException("scope 는 필수입니다.");
        }
        if (request.active() == null) {
            throw new IllegalArgumentException("active 는 필수입니다.");
        }

        Boolean active = request.active();
        OffsetDateTime now = OffsetDateTime.now();

        return switch (request.scope()) {
            case ALL -> deviceRepository.updateActiveAll(active, now);
            case CATEGORY -> {
                if (request.categoryId() == null) {
                    throw new IllegalArgumentException("scope=CATEGORY 인 경우 categoryId 는 필수입니다.");
                }
                List<Long> ids = deviceCategoryCascadeService.resolveSmallCategoryIdsForFilter(request.categoryId());
                if (ids == null || ids.isEmpty()) {
                    yield 0;
                }
                yield deviceRepository.updateActiveByCategoryIds(ids, active, now);
            }
            case DEVICES -> {
                List<Long> deviceIds = request.deviceIds();
                if (deviceIds == null || deviceIds.isEmpty()) {
                    throw new IllegalArgumentException("scope=DEVICES 인 경우 deviceIds 는 필수입니다.");
                }
                yield deviceRepository.updateActiveByDeviceIds(deviceIds, active, now);
            }
        };
    }
    //endregion

    @Transactional
    public void deleteDevice(Long deviceId) {
        deviceCategoryCascadeService.deleteSingleDevice(deviceId);
    }

//    @Transactional(readOnly = true)
//    public List<DevicePointResponse> getDevicePoints(Long deviceId) {
//        Device device = deviceRepository.fetchDeviceWithPoints().stream()
//                .filter(d -> d.getDeviceId().equals(deviceId))
//                .findFirst()
//                .orElseThrow(() -> new RuntimeException("장비를 찾을 수 없습니다. id=" + deviceId));
//        return device.getDevicePoints().stream()
//                .map(DevicePointResponse::from)
//                .toList();
//    }

    @Transactional
    public void postCctvDeviceMapping(Long deviceId, List<Long> cctvIds) {
        deviceCctvMappingRepository.deleteByDeviceId(deviceId);
        if (cctvIds != null && !cctvIds.isEmpty()) {
            deviceCctvMappingRepository.bulkInsertMappings(deviceId, cctvIds);
        }
    }

    @Transactional(readOnly = true)
    public List<CctvInfoProjection> getCctvDeviceMapping(Long deviceId) {
        return deviceCctvMappingRepository.findByDeviceId(deviceId);
    }
}
