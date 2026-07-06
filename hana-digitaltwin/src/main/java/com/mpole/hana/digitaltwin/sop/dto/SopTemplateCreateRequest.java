package com.mpole.hana.digitaltwin.sop.dto;

import jakarta.validation.constraints.NotBlank;

public record SopTemplateCreateRequest(
        @NotBlank String templateCode,
        @NotBlank String templateName,
        String description,
        Integer timeoutMinutes
) {
}
