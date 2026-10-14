package com.mpole.hana.digitaltwin.event.dto;

/**
 * 이벤트 이력 화면 상단 집계 — 같은 필터(카테고리·검색어·기간 등)로 센 건수.
 *
 * @param total     발행 이벤트 수
 * @param completed 완료(관제자 확인, {@code acked_at} 존재) 수
 * @param open      미완료(미확인) 수 = total − completed
 */
public record AlarmHistoryStats(long total, long completed, long open) {

    public static AlarmHistoryStats of(long total, long completed) {
        return new AlarmHistoryStats(total, completed, total - completed);
    }
}
