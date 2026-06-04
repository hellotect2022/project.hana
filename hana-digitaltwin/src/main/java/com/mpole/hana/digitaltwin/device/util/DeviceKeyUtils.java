package com.mpole.hana.digitaltwin.device.util;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;

/**
 * device_key = {대코드}_{중코드}_{소코드}_{장비이름}
 */
public final class DeviceKeyUtils {

    private DeviceKeyUtils() {
    }

    public static String resolveCategoryCode(DeviceCategory category) {
        if (category == null) {
            return "";
        }
        if (category.getCategoryCode() != null && !category.getCategoryCode().isBlank()) {
            return category.getCategoryCode().trim();
        }
        return sanitize(category.getCategoryName());
    }

    public static String buildDeviceKey(
            DeviceCategory major,
            DeviceCategory mid,
            DeviceCategory small,
            String deviceName
    ) {
        String majorCode = resolveCategoryCode(major);
        String midCode = resolveCategoryCode(mid);
        String smallCode = resolveCategoryCode(small);
        String name = deviceName != null ? deviceName.trim() : "";
        if (majorCode.isEmpty() || midCode.isEmpty() || smallCode.isEmpty() || name.isEmpty()) {
            throw new IllegalStateException("device_key를 생성할 수 없습니다.");
        }
        return majorCode + "_" + midCode + "_" + smallCode + "_" + name;
    }

    public static String buildPointKey(String deviceKey, String tagName) {
        String key = deviceKey != null ? deviceKey.trim() : "";
        String tag = tagName != null ? tagName.trim() : "";
        if (key.isEmpty() || tag.isEmpty()) {
            throw new IllegalStateException("point_key를 생성할 수 없습니다.");
        }
        return key + "_" + tag;
    }

    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^a-zA-Z0-9_가-힣-]", "");
    }
}
