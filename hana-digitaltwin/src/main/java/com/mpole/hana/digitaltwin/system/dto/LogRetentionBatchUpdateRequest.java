package com.mpole.hana.digitaltwin.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record LogRetentionBatchUpdateRequest(
        @NotEmpty @Valid List<LogRetentionPolicyRequest> policies
) {
}
