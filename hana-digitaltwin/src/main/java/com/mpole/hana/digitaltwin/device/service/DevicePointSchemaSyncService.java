package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.device.repository.DevicePointRepository;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 카테고리 스키마(TAG) 변경 시 해당 카테고리 장비의 {@code tbl_device_point} 동기화.
 */
@Service
@RequiredArgsConstructor
public class DevicePointSchemaSyncService {

    private final DeviceRepository deviceRepository;
    private final DevicePointRepository devicePointRepository;

    @Transactional
    public void syncForCategory(
            Long categoryId,
            List<PointSchemaDefinition> oldSchema,
            List<PointSchemaDefinition> newSchema
    ) {
        Set<String> oldTags = tagNames(oldSchema);
        Set<String> newTags = tagNames(newSchema);

        Set<String> removed = new HashSet<>(oldTags);
        removed.removeAll(newTags);

        List<Device> devices = deviceRepository.fetchTargetCategoryDevices(categoryId);
        if (devices.isEmpty()) {
            return;
        }

        for (Device device : devices) {
            Long deviceId = device.getDeviceId();
            if (!removed.isEmpty()) {
                devicePointRepository.deleteByDevice_DeviceIdAndTagNameIn(deviceId, removed);
            }
            for (PointSchemaDefinition def : newSchema) {
                String tagName = normalizeTag(def.tagName());
                if (tagName.isEmpty()) {
                    continue;
                }
                if (devicePointRepository.existsByDevice_DeviceIdAndTagName(deviceId, tagName)) {
                    continue;
                }
                String deviceKey = device.getDeviceKey();
                String pointKey = (deviceKey != null && !deviceKey.isBlank())
                        ? deviceKey.trim() + "_" + tagName
                        : tagName;
                if (devicePointRepository.existsByPointKey(pointKey)) {
                    continue;
                }
                DevicePoint point = DevicePoint.builder()
                        .device(device)
                        .pointKey(pointKey)
                        .tagName(tagName)
                        .pointName(def.tagDesc() != null && !def.tagDesc().isBlank() ? def.tagDesc().trim() : tagName)
                        .pointType(def.type())
                        .unit(def.unit())
                        .active(def.isDisplay())
                        .build();
                devicePointRepository.save(point);
            }
        }
    }

    private static Set<String> tagNames(List<PointSchemaDefinition> schema) {
        if (schema == null || schema.isEmpty()) {
            return Set.of();
        }
        return schema.stream()
                .map(PointSchemaDefinition::tagName)
                .map(DevicePointSchemaSyncService::normalizeTag)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static String normalizeTag(String tagName) {
        return tagName != null ? tagName.trim() : "";
    }
}
