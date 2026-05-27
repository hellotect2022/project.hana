package com.mpole.hana.digitaltwin.audit.dto;

public record AuditLogPurgeResult(
        int deletedAccessRows,
        int deletedUsageRows
) {
}
