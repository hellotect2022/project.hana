package com.mpole.hana.digitaltwin.device.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record DevicePointMappingPageResponse(
        List<DevicePointMappingItemResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        boolean first,
        boolean last,
        long mappedCount
) {
    public static DevicePointMappingPageResponse of(
            Page<DevicePointMappingItemResponse> page,
            long mappedCount
    ) {
        return new DevicePointMappingPageResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                page.isFirst(),
                page.isLast(),
                mappedCount
        );
    }
}
