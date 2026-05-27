package com.mpole.hana.digitaltwin.systemMenu.dto;

import jakarta.validation.constraints.NotNull;

public record DeviceSystemSortOrderRequest(
        @NotNull(message = "systemId는 필수입니다.") Long systemId,
        @NotNull(message = "sortOrder는 필수입니다.") Integer sortOrder
) {
}
