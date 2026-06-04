package com.mpole.hana.digitaltwin.device.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 장비 등록 시 함께 생성할 포인트 정보
 * - tag_name: schemaTagName (스키마 원본, 예: test3)
 * - point_key: {deviceKey}_{schemaTagName} (예: 전기설비_전력제어_SUBE_test_test3)
 */
public record DevicePointRegisterItem(
        String tagName,
        @JsonAlias("point_key")
        String pointKey,
        String schemaTagName,
        String pointName,
        String pointType,
        String unit,
        String tagDesc,
        Boolean isDisplay
) {
}
