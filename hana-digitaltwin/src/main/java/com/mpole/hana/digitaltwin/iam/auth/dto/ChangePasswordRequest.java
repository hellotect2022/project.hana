package com.mpole.hana.digitaltwin.iam.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        //@NotBlank @Size(min = 8) String newPassword,
        @NotBlank String newPassword,
        @NotBlank String confirmPassword
) {
}
