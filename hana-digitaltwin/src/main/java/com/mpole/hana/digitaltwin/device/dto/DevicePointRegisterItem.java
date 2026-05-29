package com.mpole.hana.digitaltwin.device.dto;

/**
 * 장비 등록 시 함께 저장할 관제점 항목.
 */
public record DevicePointRegisterItem(
        String schemaTagName,
        String tagName,
        String pointKey,
        String pointName,
        String pointType,
        String unit,
        Boolean isDisplay
) {
}
