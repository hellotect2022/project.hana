package com.mpole.hana.digitaltwin.space.dto;

/** 구역 현황 통계 카드 — 전체/정상(ON)/소등(OFF)/Fault */
public record ZoneStatusSummaryResponse(
        long total,
        long on,
        long off,
        long fault
) {
}
