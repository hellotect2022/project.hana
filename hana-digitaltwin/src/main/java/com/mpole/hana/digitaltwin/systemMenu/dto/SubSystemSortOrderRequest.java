package com.mpole.hana.digitaltwin.systemMenu.dto;

import jakarta.validation.constraints.NotNull;

public record SubSystemSortOrderRequest(
        @NotNull(message = "subSystemId는 필수입니다.") Long subSystemId,
        @NotNull(message = "sortOrder는 필수입니다.") Integer sortOrder
) {
}
