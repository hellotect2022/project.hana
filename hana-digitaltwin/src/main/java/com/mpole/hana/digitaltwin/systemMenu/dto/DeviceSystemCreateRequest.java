package com.mpole.hana.digitaltwin.systemMenu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DeviceSystemCreateRequest(
        @NotBlank(message = "시스템명은 필수입니다.") String systemName,
        @NotBlank(message = "시스템 코드는 필수입니다.") String systemCode,
        @NotNull(message = "정렬 순서는 필수입니다.") Integer sortOrder,
        List<Long> categoryIds
) {
}
