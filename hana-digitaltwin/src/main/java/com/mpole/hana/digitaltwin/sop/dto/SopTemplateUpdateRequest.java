package com.mpole.hana.digitaltwin.sop.dto;

import jakarta.validation.constraints.NotBlank;

public record SopTemplateUpdateRequest(
        @NotBlank String templateName,
        String description,
        Boolean active
) {
}
