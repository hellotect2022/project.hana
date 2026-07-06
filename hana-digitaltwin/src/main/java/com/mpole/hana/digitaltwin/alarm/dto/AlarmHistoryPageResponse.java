package com.mpole.hana.digitaltwin.alarm.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 알람 이력 목록 페이징 응답.
 */
public record AlarmHistoryPageResponse(
        List<AlarmHistoryResponse> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        boolean first,
        boolean last
) {
    public static AlarmHistoryPageResponse from(Page<AlarmHistoryResponse> page) {
        return new AlarmHistoryPageResponse(
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
