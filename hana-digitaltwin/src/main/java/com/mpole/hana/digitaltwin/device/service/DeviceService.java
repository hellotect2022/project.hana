package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.digitaltwin.device.dto.CctvInfoProjection;
import com.mpole.hana.digitaltwin.device.dto.DevicePointRegisterItem;
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
import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import com.mpole.hana.digitaltwin.unityAsset.service.UnityAssetService;
import com.mpole.hana.digitaltwin.realtime.api.DeviceRealtimeController;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {
    private final DeviceRepository deviceRepository;
    private final DeviceCategoryRepository deviceCategoryRepository;
    private final DeviceCctvMappingRepository deviceCctvMappingRepository;
    private final DevicePointRepository devicePointRepository;
    private final UnityAssetService unityAssetService;

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

        if (request.assetId() != null) {
            UnityAsset asset = unityAssetService.getAssetOrThrow(request.assetId());
            device.setUnityAsset(asset);
        }

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
        return deviceRepository.fetchAllDevices().stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse getTargetDevice(Long deviceId) {
        Device device = deviceRepository.fetchDeviceById(deviceId);
        return DeviceResponse.from(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getSearchDevices(Long floorId, Long zoneId) {
        return deviceRepository.fetchSearchDevices(floorId, zoneId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getTargetCategoryDevices(Long categoryId) {
        return deviceRepository.fetchTargetCategoryDevices(categoryId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getTargetCategoryDevicesCnt(Long categoryId) {
        return deviceRepository.countByCategory_CategoryId(categoryId);
    }

    @Transactional
    public DeviceResponse updateDevice(Long deviceId, DeviceUpdateRequest request) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("장비를 찾을 수 없습니다. id=" + deviceId));
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
        return DeviceResponse.from(deviceRepository.fetchDeviceById(deviceId));
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
