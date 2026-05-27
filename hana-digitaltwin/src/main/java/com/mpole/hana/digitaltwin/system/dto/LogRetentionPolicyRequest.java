package com.mpole.hana.digitaltwin.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LogRetentionPolicyRequest(
        @NotBlank String policyKey,
        @Min(1) @Max(365) int retentionDays
) {
}
