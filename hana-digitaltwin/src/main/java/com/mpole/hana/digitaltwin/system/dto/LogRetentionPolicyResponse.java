package com.mpole.hana.digitaltwin.system.dto;

import com.mpole.hana.digitaltwin.system.entity.LogRetentionPolicy;
import lombok.Builder;

@Builder
public record LogRetentionPolicyResponse(
        String policyKey,
        String label,
        int retentionDays,
        String description,
        String updatedAt
) {
    public static LogRetentionPolicyResponse from(LogRetentionPolicy policy, String label) {
        return LogRetentionPolicyResponse.builder()
                .policyKey(policy.getPolicyKey())
                .label(label)
                .retentionDays(policy.getRetentionDays())
                .description(policy.getDescription())
                .updatedAt(policy.getUpdatedAt() != null ? policy.getUpdatedAt().toString() : null)
                .build();
    }
}
