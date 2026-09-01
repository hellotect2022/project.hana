package com.mpole.hana.digitaltwin.device.schema;

import com.fasterxml.jackson.annotation.JsonCreator;

public record PointSchemaDefinition(
        String tagName,
        String type,
        String unit,
        String tagDesc,
        String tagDisplayName,
        boolean isDisplay
) {
    @JsonCreator
    public PointSchemaDefinition {
        // 이 컴팩트 생성자 형식을 사용하면 Jackson이 필드 이름과 매칭하여 역직렬화합니다.
    }
}
