package com.mpole.hana.digitaltwin.systemMenu.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record DeviceSystemUpdateRequest(
        @NotBlank(message = "시스템명은 필수입니다.") String systemName,
        @NotBlank(message = "시스템 코드는 필수입니다.") String systemCode,
        Boolean active,
        // 직접 매핑(subSystem 없는 시스템)용 소분류 카테고리 ID 목록. optional.
        List<Long> categoryIds
) {
}
