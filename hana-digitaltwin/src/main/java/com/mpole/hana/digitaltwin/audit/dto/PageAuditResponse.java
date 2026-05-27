package com.mpole.hana.digitaltwin.audit.dto;

import java.util.List;

public record PageAuditResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size
) {
}
