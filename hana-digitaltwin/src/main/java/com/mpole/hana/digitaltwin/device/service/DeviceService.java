package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.common.constant.PointDataSource;
import com.mpole.hana.digitaltwin.command.repository.CommandPointRepository;
import com.mpole.hana.digitaltwin.device.dto.BulkActiveRequest;
import com.mpole.hana.digitaltwin.device.dto.CctvCameraResponse;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.digitaltwin.device.dto.DevicePropertyTypeResponse;
import com.mpole.hana.digitaltwin.device.entity.DeviceCctvMapping;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.device.property.DevicePropertyInfo;
import com.mpole.hana.digitaltwin.device.property.DevicePropertyType;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import com.mpole.hana.digitaltwin.device.dto.DeviceResponseWithDiagram;
import com.mpole.hana.digitaltwin.device.dto.DeviceWiringResponse;
import com.mpole.hana.digitaltwin.infra.repository.DeviceInfraRepository;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceCurrentValueRepository;
import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagramWiring;
import com.mpole.hana.digitaltwin.systemDiagram.repository.SystemDiagramWiringRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {
    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceCctvMappingRepository deviceCctvMappingRepository;
    private final DevicePointRepository devicePointRepository;
    private final DeviceCategoryCascadeService deviceCategoryCascadeService;
    private final DeviceSystemRepository deviceSystemRepository;
    private final DeviceSubSystemRepository deviceSubSystemRepository;
    private final CommandPointRepository commandPointRepository;
    private final SystemDiagramWiringRepository systemDiagramWiringRepository;
    private final DeviceCurrentValueRepository deviceCurrentValueRepository;
    private final DeviceInfraRepository deviceInfraRepository;
    private final ObjectMapper objectMapper;

    /**
     * 장비 property 타입 메타 — 각 DevicePropertyType 과 property_info 스켈레톤(클래스에서 파생).
     * 카테고리 property_type 선택 / 장비 등록 시 property_info 양식 안내에 사용.
     */
    @Transactional(readOnly = true)
    public List<DevicePropertyTypeResponse> getPropertyTypes() {
        List<DevicePropertyTypeResponse> out = new ArrayList<>();
        for (DevicePropertyType v : DevicePropertyType.values()) {
            JsonNode template;
            try {
                // 폴리모픽 변환: {type:X} → 해당 property 클래스 인스턴스 → 직렬화(모든 필드 null 스켈레톤)
                DevicePropertyInfo inst = objectMapper.convertValue(Map.of("type", v.getJsonName()), DevicePropertyInfo.class);
                template = objectMapper.valueToTree(inst);
            } catch (Exception e) {
                template = objectMapper.createObjectNode().put("type", v.getJsonName());
            }
            out.add(new DevicePropertyTypeResponse(v.name(), template));
        }
        return out;
    }

    @Transactional
    public DeviceResponse saveDevice(DeviceUpdateRequest request) {
        if (request.deviceKey() != null && deviceRepository.existsByDeviceKey(request.deviceKey())) {
            throw new IllegalStateException("이미 사용 중인 deviceKey 입니다: " + request.deviceKey());
        }

        Device device = Device.builder()
                .deviceName(request.deviceName())
                .deviceDisplayName(request.deviceDisplayName())
                .deviceKey(request.deviceKey())
                .active(request.active())
                .description(request.description())
                .propertyInfo(request.propertyInfo())
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
            validatePropertyType(category, request.propertyInfo());
        }

        Device saved = deviceRepository.save(device);
        saveDevicePoints(saved, request.points());
        return DeviceResponse.from(deviceRepository.fetchDeviceById(saved.getDeviceId()));
    }

    /** 카테고리 property_type 과 요청 property_info 타입 정합성 검증(둘 다 명시된 경우만). */
    private void validatePropertyType(DeviceCategory category, DevicePropertyInfo propertyInfo) {
        if (category.getPropertyType() == null || propertyInfo == null) {
            return;
        }
        DevicePropertyType actual = propertyInfo.propertyType();
        if (actual != null && actual != category.getPropertyType()) {
            throw new IllegalStateException(
                    "property_info 타입(" + actual + ")이 카테고리 property_type(" + category.getPropertyType() + ")과 다릅니다.");
        }
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
            String description = item.description() != null && !item.description().isBlank()
                    ? item.description().trim()
                    : tagName;

            // 미지정(null)이면 하위호환으로 VIEW01 취급된다. RabbitMQ/파생값 포인트는 반드시 지정해야 한다.
            PointDataSource dataSource = null;
            String rawSource = trimToNull(item.dataSource());
            if (rawSource != null) {
                dataSource = PointDataSource.fromCode(rawSource);
                if (dataSource == null) {
                    throw new IllegalArgumentException(
                            "지원하지 않는 data_source 입니다: " + rawSource + " (pointKey=" + pointKey + ")"
                    );
                }
            }

            DevicePoint point = DevicePoint.builder()
                    .device(device)
                    .pointKey(pointKey)
                    .tagName(tagName)
                    .description(description)
                    .pointDisplayName(item.pointDisplayName())
                    .pointType(item.pointType())
                    .unit(item.unit())
                    .active(item.isDisplay() == null || item.isDisplay())
                    .dataSource(dataSource)
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
                                cp.getPoint() != null ? cp.getPoint().getPointId() : null,
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

            // 1:N 팬아웃 허용 — 같은 (ref_device_code, ref_point_code) 를 여러 point 가 가질 수 있으므로
            // 조합 중복 거부 규칙 제거. (both-or-neither 만 유지)

            point.setRefDeviceCode(refDeviceCode);
            point.setRefPointCode(refPointCode);

            // data_source: null 이면 기존 값 유지(비우지 않는다).
            // 이 필드를 모르는 기존 클라이언트가 매핑을 저장할 때 AMQP/DERIVED 지정이 지워지면
            // 그 포인트가 VIEW01 로 간주되어 gateway 발행 대상이 되고 값이 null 로 덮인다.
            String rawSource = trimToNull(request.dataSource());
            if (rawSource != null) {
                PointDataSource parsed = PointDataSource.fromCode(rawSource);
                if (parsed == null) {
                    throw new IllegalArgumentException(
                            "지원하지 않는 data_source 입니다: " + rawSource + " (pointId=" + request.pointId() + ")"
                    );
                }
                point.setDataSource(parsed);
            }
        }

        // 인프라 소속 자동 산정 훅(설계 v2.2): 리매핑된 장비 중 infra_id 미지정(NULL) 장비만 재산정.
        // (수동 지정은 덮지 않음. 공유신호 제외 목록은 관리자 auto-assign 액션에서 적용.)
        Set<Long> touchedDeviceIds = requests.stream()
                .map(r -> devicePointRepository.findById(r.pointId())
                        .map(p -> p.getDevice() != null ? p.getDevice().getDeviceId() : null)
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!touchedDeviceIds.isEmpty()) {
            int assigned = deviceInfraRepository.autoAssignForDevices(touchedDeviceIds);
            if (assigned > 0) {
                log.info("[DeviceInfra] 포인트 리매핑 훅 — {}개 장비 인프라 자동 매핑", assigned);
            }
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

    /**
     * {@link #getTargetCategoryDevices} 결과에 계통도 배선(wiring)을 붙여 반환한다.
     */
    @Transactional(readOnly = true)
    public Page<DeviceResponseWithDiagram> getTargetCategoryDevicesWithDiagram(Long categoryId, Pageable pageable) {
        return attachWirings(getTargetCategoryDevices(categoryId, pageable));
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

    /**
     * {@link #getDevicesBySystemId} 결과에 계통도 배선(wiring)을 붙여 반환한다.
     */
    @Transactional(readOnly = true)
    public Page<DeviceResponseWithDiagram> getDevicesWithDiagramBySystemId(Long systemId, Pageable pageable) {
        return attachWirings(getDevicesBySystemId(systemId, pageable));
    }

    /**
     * {@link #getDevicesBySubSystemId} 결과에 계통도 배선(wiring)을 붙여 반환한다.
     */
    @Transactional(readOnly = true)
    public Page<DeviceResponseWithDiagram> getDevicesWithDiagramBySubSystemId(Long subSystemId, Pageable pageable) {
        return attachWirings(getDevicesBySubSystemId(subSystemId, pageable));
    }

    /**
     * 페이지 내 디바이스들에 연결된 배선(from/to 어느 쪽이든)을 배치 1쿼리로 조회해 각 디바이스에 매핑한다.
     * 한 배선의 양끝(from·to)이 모두 페이지에 있으면 각 디바이스에 자기 관점(direction/상대편)으로 붙는다.
     */
    private Page<DeviceResponseWithDiagram> attachWirings(Page<DeviceResponse> page) {
        List<Long> deviceIds = page.getContent().stream()
                .map(DeviceResponse::deviceId)
                .toList();
        if (deviceIds.isEmpty()) {
            return page.map(d -> new DeviceResponseWithDiagram(d, List.of()));
        }

        Set<Long> pageIds = new HashSet<>(deviceIds);
        List<SystemDiagramWiring> wirings = systemDiagramWiringRepository.findConnectedToDevices(deviceIds);

        Map<Long, List<DeviceWiringResponse>> byDevice = new HashMap<>();
        for (SystemDiagramWiring w : wirings) {
            Long fromId = w.getFromDevice() != null ? w.getFromDevice().getDeviceId() : null;
            Long toId = w.getToDevice() != null ? w.getToDevice().getDeviceId() : null;
            if (fromId != null && pageIds.contains(fromId)) {
                byDevice.computeIfAbsent(fromId, k -> new ArrayList<>()).add(DeviceWiringResponse.from(w, fromId));
            }
            if (toId != null && pageIds.contains(toId)) {
                byDevice.computeIfAbsent(toId, k -> new ArrayList<>()).add(DeviceWiringResponse.from(w, toId));
            }
        }

        return page.map(d -> new DeviceResponseWithDiagram(d, byDevice.getOrDefault(d.deviceId(), List.of())));
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
        if (request.deviceDisplayName() != null) {
            device.setDeviceDisplayName(request.deviceDisplayName());
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

        // property_info 갱신(제공된 경우만 — 생략 시 기존 유지). 카테고리 타입과 정합성 검증.
        if (request.propertyInfo() != null) {
            if (device.getCategory() != null) {
                validatePropertyType(device.getCategory(), request.propertyInfo());
            }
            device.setPropertyInfo(request.propertyInfo());
        }

        // 상태값 갱신(제공된 경우만). alarm_state=false 면 hana-event 평가기가 알람 억제(캐시 반영 지연 존재).
        if (request.alarmState() != null) {
            device.setAlarmState(request.alarmState());
        }
        if (request.operationState() != null) {
            device.setOperationState(request.operationState());
        }

        // 소속 인프라 지정(제공된 경우만). 해제(null 설정)는 PUT /api/device-infra/mapping 경유.
        if (request.infraId() != null) {
            device.setInfraId(request.infraId());
        }

        // 통신상태(v2.1): 저장소는 tbl_device_current_value.connection_state.
        // 인프라 소속 장비는 자동 상속(reconcile) 소유라 수동 변경 거부, 미소속(MQTT 등)만 타깃 upsert.
        if (request.connectionState() != null) {
            if (device.getInfraId() != null) {
                throw new IllegalArgumentException(
                        "인프라 소속 장비의 connectionState 는 인프라 상태에서 자동 상속됩니다(수동 변경 불가). deviceId=" + deviceId);
            }
            deviceCurrentValueRepository.upsertConnectionState(
                    deviceId, device.getDeviceKey(), request.connectionState().name());
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

    /**
     * 장비 주변 CCTV 매핑 저장(replace). cctvIds 의 순서를 sort_order 로 보존(이벤트 8분할 배치 순서),
     * 첫 번째를 대표(isPrimary)로 설정. 기존 매핑은 전부 삭제 후 재삽입.
     */
    @Transactional
    public void postCctvDeviceMapping(Long deviceId, List<Long> cctvIds) {
        deviceCctvMappingRepository.deleteByDeviceId(deviceId);
        if (cctvIds == null || cctvIds.isEmpty()) {
            return;
        }
        Device device = deviceRepository.getReferenceById(deviceId);
        List<DeviceCctvMapping> rows = new ArrayList<>();
        for (int i = 0; i < cctvIds.size(); i++) {
            Device cctv = deviceRepository.getReferenceById(cctvIds.get(i));
            rows.add(DeviceCctvMapping.builder()
                    .device(device)
                    .cctv(cctv)
                    .sortOrder(i)
                    .primary(i == 0)
                    .build());
        }
        deviceCctvMappingRepository.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public List<CctvCameraResponse> getCctvDeviceMapping(Long deviceId) {
        return deviceCctvMappingRepository.findByDeviceId(deviceId).stream()
                .map(CctvCameraResponse::from)
                .toList();
    }

    /** CCTV 매핑 피커용 — property_info.type 이 CCTV 인 장비 후보 전체. */
    @Transactional(readOnly = true)
    public List<CctvCameraResponse> getCctvCandidates() {
        return deviceCctvMappingRepository.findCctvCandidates(DevicePropertyType.CCTV.getJsonName()).stream()
                .map(CctvCameraResponse::from)
                .toList();
    }
}
