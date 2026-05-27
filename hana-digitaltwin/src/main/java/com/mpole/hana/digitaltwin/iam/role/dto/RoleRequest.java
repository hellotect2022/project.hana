package com.mpole.hana.digitaltwin.iam.role.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleRequest(
        @NotBlank String roleName,
        String description
) {
}
