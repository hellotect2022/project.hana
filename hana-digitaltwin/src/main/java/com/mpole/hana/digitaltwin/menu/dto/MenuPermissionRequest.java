package com.mpole.hana.digitaltwin.menu.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MenuPermissionRequest(
        @NotNull(message = "menuIds는 필수입니다.") List<Long> menuIds
) {
}
