package com.mpole.hana.digitaltwin.systemMenu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubSystemCreateRequest(
        @NotNull(message = "systemId는 필수입니다.") Long systemId,
        @NotBlank(message = "하위 시스템명은 필수입니다.") String subSystemName,
        Integer sortOrder,
        Boolean active,
        List<Long> categoryIds
) {
}
