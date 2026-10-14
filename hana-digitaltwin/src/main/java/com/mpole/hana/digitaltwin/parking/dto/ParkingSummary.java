package com.mpole.hana.digitaltwin.parking.dto;

import java.time.OffsetDateTime;

/**
 * 주차 집계 (2026-10-13).
 *
 * @param from    집계 시작(포함). null 이면 전체 기간
 * @param to      집계 끝(미포함)
 * @param inCount  기간 안 입차 수
 * @param outCount 기간 안 출차 수
 * @param parked   지금 주차중인 차량 수(기간과 무관 — 입차 기록이 있고 아직 안 나간 건)
 */
public record ParkingSummary(
        OffsetDateTime from,
        OffsetDateTime to,
        long inCount,
        long outCount,
        long parked
) {
}
