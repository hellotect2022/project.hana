package com.mpole.hana.digitaltwin.systemMenu.dto;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSubSystem;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystem;
import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystemCategory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DeviceSystemResponse(
        Long systemId,
        String systemName,
        String systemCode,
        Integer sortOrder,
        Boolean active,
        // 3단 구조: 시스템 → subSystem → 카테고리
        List<SubSystemBrief> subSystems,
        // 레거시(하위호환): 시스템에 직접 매핑된 카테고리. 3단 전환 후 신규 매핑은 비어있음.
        List<DeviceSystemCategoryBrief> categories
) {
    public record SubSystemBrief(
            Long subSystemId,
            String subSystemName,
            Integer sortOrder,
            Boolean active,
            List<DeviceSystemCategoryBrief> categories
    ) {
    }

    public static DeviceSystemResponse from(DeviceSystem system) {
        // JOIN FETCH(subSystems + 각 subSystem의 categoryMappings) 카티전 곱으로
        // subSystems List 에 같은 subSystem 이 카테고리 수만큼 중복 담길 수 있어 id 기준 중복 제거.
        Map<Long, DeviceSubSystem> uniqueSubs = new LinkedHashMap<>();
        for (DeviceSubSystem s : system.getSubSystems()) {
            uniqueSubs.putIfAbsent(s.getSubSystemId(), s);
        }
        List<SubSystemBrief> subSystems = uniqueSubs.values().stream()
                .sorted((a, b) -> Integer.compare(
                        a.getSortOrder() != null ? a.getSortOrder() : 0,
                        b.getSortOrder() != null ? b.getSortOrder() : 0))
                .map(DeviceSystemResponse::toSubSystemBrief)
                .toList();

        // 직접 매핑(deviceSubSystem == null)만 categories[] 에 담는다.
        // subSystem 밑 카테고리는 subSystems[] 안에만 노출한다.
        List<DeviceSystemCategoryBrief> categories = system.getCategoryMappings().stream()
                .filter(m -> m.getDeviceSubSystem() == null)
                .map(m -> toBrief(m.getCategory()))
                .toList();

        return new DeviceSystemResponse(
                system.getSystemId(),
                system.getSystemName(),
                system.getSystemCode(),
                system.getSortOrder(),
                system.getActive(),
                subSystems,
                categories
        );
    }

    private static SubSystemBrief toSubSystemBrief(DeviceSubSystem subSystem) {
        List<DeviceSystemCategoryBrief> categories = subSystem.getCategoryMappings().stream()
                .map(m -> toBrief(m.getCategory()))
                .toList();
        return new SubSystemBrief(
                subSystem.getSubSystemId(),
                subSystem.getSubSystemName(),
                subSystem.getSortOrder(),
                subSystem.getActive(),
                categories
        );
    }

    private static DeviceSystemCategoryBrief toBrief(DeviceCategory category) {
        return new DeviceSystemCategoryBrief(
                category.getCategoryId(),
                category.getCategoryName(),
                category.getCategoryCode()
        );
    }
}
