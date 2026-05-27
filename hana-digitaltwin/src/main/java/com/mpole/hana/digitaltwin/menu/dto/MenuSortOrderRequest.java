package com.mpole.hana.digitaltwin.menu.dto;

import jakarta.validation.constraints.NotNull;

public record MenuSortOrderRequest(
        @NotNull(message = "menuId는 필수입니다.") Long menuId,
        @NotNull(message = "sortOrder는 필수입니다.") Integer sortOrder
) {
}
