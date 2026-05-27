package com.mpole.hana.digitaltwin.iam.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserUpdateRequest(
        @NotBlank String username,
        @Email String email,
        String password,
        Boolean active,
        Boolean accountNonLocked,
        Integer failedLoginAttempts
) {
}
