package com.mpole.hana.digitaltwin.system.dto;

public record LogRetentionPurgeResponse(
        int deletedAccessRows,
        int deletedUsageRows,
        int accessRetentionDaysApplied,
        int usageRetentionDaysApplied
) {
}
