package com.mpole.hana.digitaltwin.iam.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserCreateRequest(
        @NotBlank String loginId,
        @NotBlank String username,
        @Email String email,
        @NotBlank String password,
        @NotNull Boolean active
) {
}
