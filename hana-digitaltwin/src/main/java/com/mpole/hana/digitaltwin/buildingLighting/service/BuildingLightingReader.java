package com.mpole.hana.digitaltwin.buildingLighting.service;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.buildingLighting.config.BuildingLightingProperties;
import com.mpole.hana.digitaltwin.buildingLighting.domain.CommStatus;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingReading;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingState;
import com.mpole.hana.digitaltwin.buildingLighting.repository.LightingDeviceRepository;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePlacement;
import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;
import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystem;
import com.mpole.hana.digitaltwin.systemMenu.repository.DeviceSystemRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 건물조명 장비를 로드하고 실시간 포인트를 조명 의미({@link LightingReading})로 파싱하는 공통 리더.
 * 운용현황/구역별/층별 집계와 제어 대상 산출이 모두 이 결과를 입력으로 사용한다.
 */
@Service
@RequiredArgsConstructor
public class BuildingLightingReader {

    private final DeviceSystemRepository deviceSystemRepository;
    private final LightingDeviceRepository lightingDeviceRepository;
    private final BuildingLightingProperties props;

    /** 건물조명 시스템에 매핑된 소분류 카테고리 ID 목록 */
    @Transactional(readOnly = true)
    public List<Long> resolveCategoryIds() {
        DeviceSystem system = deviceSystemRepository.findByIdWithCategories(props.getSystemId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "건물조명 BMS 시스템이 존재하지 않습니다. systemId=" + props.getSystemId()));
        return system.getCategoryMappings().stream()
                .map(mapping -> mapping.getCategory().getCategoryId())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LightingReading> readAll() {
        List<Long> categoryIds = resolveCategoryIds();
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return toReadings(lightingDeviceRepository.fetchForLighting(categoryIds));
    }

    @Transactional(readOnly = true)
    public List<LightingReading> readByFloor(Long floorId) {
        List<Long> categoryIds = resolveCategoryIds();
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return toReadings(lightingDeviceRepository.fetchForLightingByFloor(categoryIds, floorId));
    }

    @Transactional(readOnly = true)
    public List<LightingReading> readByZone(Long zoneId) {
        List<Long> categoryIds = resolveCategoryIds();
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return toReadings(lightingDeviceRepository.fetchForLightingByZone(categoryIds, zoneId));
    }

    private List<LightingReading> toReadings(List<Device> devices) {
        OffsetDateTime now = OffsetDateTime.now();
        return devices.stream()
                .map(device -> toReading(device, now))
                .toList();
    }

    private LightingReading toReading(Device device, OffsetDateTime now) {
        DevicePlacement placement = device.getPlacement();
        UnityLocZone zone = placement != null ? placement.getUnityZone() : null;
        UnityLocFloor floor = zone != null ? zone.getFloor() : null;
        DeviceCurrentValue currentValue = device.getDeviceCurrentValue();
        List<XnPointData> points = currentValue != null ? currentValue.getPoints() : null;

        LightingState state = LightingState.UNKNOWN;
        Integer dimming = null;
        boolean anyAlarm = false;

        if (points != null) {
            for (XnPointData point : points) {
                if ("Y".equalsIgnoreCase(point.alarmYn())) {
                    anyAlarm = true;
                }
                if (props.getOnoffTag().equalsIgnoreCase(point.tagName())) {
                    state = LightingState.fromRaw(point.valueRaw());
                } else if (props.getDimmingTag().equalsIgnoreCase(point.tagName())) {
                    dimming = parseDimming(point.valueRaw());
                }
            }
        }

        CommStatus commStatus = resolveCommStatus(currentValue, anyAlarm, now);

        return new LightingReading(
                device.getDeviceId(),
                device.getDeviceName(),
                floor != null ? floor.getFloorId() : null,
                floor != null ? floor.getFloorName() : null,
                floor != null ? floor.getFloorNum() : null,
                zone != null ? zone.getZoneId() : null,
                zone != null ? zone.getZoneName() : null,
                state,
                dimming,
                commStatus,
                currentValue != null ? currentValue.getUpdatedAt() : null
        );
    }

    private CommStatus resolveCommStatus(DeviceCurrentValue currentValue, boolean anyAlarm, OffsetDateTime now) {
        if (anyAlarm) {
            return CommStatus.ALARM;
        }
        if (currentValue == null || currentValue.getUpdatedAt() == null) {
            return CommStatus.ABNORMAL;
        }
        if (currentValue.getUpdatedAt().isBefore(now.minusSeconds(props.getCommStaleSeconds()))) {
            return CommStatus.ABNORMAL;
        }
        return CommStatus.NORMAL;
    }

    /** 디밍 raw 값을 0~100 정수로 변환(소수/범위 보정). 변환 불가 시 null */
    private Integer parseDimming(String valueRaw) {
        if (valueRaw == null || valueRaw.isBlank()) {
            return null;
        }
        try {
            int level = Math.round(Float.parseFloat(valueRaw.trim()));
            return Math.max(0, Math.min(100, level));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
