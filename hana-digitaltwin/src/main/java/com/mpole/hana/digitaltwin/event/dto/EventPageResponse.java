package com.mpole.hana.digitaltwin.event.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 알람 이력 목록 페이징 응답.
 */
public record EventPageResponse(
        List<EventResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        boolean first,
        boolean last
) {
    public static EventPageResponse from(Page<EventResponse> page) {
        return new EventPageResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                page.isFirst(),
                page.isLast()
        );
    }
}
