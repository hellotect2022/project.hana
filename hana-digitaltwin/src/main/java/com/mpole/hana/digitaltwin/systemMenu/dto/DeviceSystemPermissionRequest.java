package com.mpole.hana.digitaltwin.systemMenu.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DeviceSystemPermissionRequest(
        @NotNull(message = "systemIds는 필수입니다.") List<Long> systemIds
) {
}
