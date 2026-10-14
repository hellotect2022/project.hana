package com.mpole.hana.digitaltwin.parking.dto;

import java.util.List;

/**
 * 주차면 점유 현황 (2026-10-14) — {@code GET /api/parking/occupancy}.
 *
 * <p>주차면 센서(분류 PA-C·PA-D·PA-E·PA-R) 장비 1대 = 1칸. 현재값 {@code st} 가 Y 면 주차, N 이면 빈자리.</p>
 *
 * @param total     전체 주차면 수(센서 장비 수)
 * @param parked    현재 주차 대수(st = Y)
 * @param available 잔여 주차 대수(st = N)
 * @param unknown   상태를 알 수 없는 칸(현재값 없음·Y/N 이 아닌 값). total = parked + available + unknown
 * @param floors    층별 — 위층부터(sortOrder 내림차순). 층 배치가 없는 센서는 floorId·floorName 이 null 인 한 줄로 맨 뒤
 */
public record ParkingOccupancy(
        long total,
        long parked,
        long available,
        long unknown,
        List<Floor> floors
) {

    public record Floor(
            Long floorId,
            String floorName,
            long total,
            long parked,
            long available,
            long unknown
    ) {
    }
}
