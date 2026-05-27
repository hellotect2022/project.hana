package com.mpole.hana.digitaltwin.audit.dto;

import jakarta.validation.constraints.NotBlank;

public record UsageLogCreateRequest(
        @NotBlank String menuLabel
) {
}
