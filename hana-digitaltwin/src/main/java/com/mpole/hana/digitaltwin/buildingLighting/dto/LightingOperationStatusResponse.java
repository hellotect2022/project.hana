package com.mpole.hana.digitaltwin.buildingLighting.dto;

import com.mpole.hana.digitaltwin.buildingLighting.domain.CommStatus;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingReading;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingState;

import java.util.List;

/**
 * UI "조명제어 운용현황"(패널 3) 응답 — 건물조명 전체 집계.
 *
 * @param total        등록된 조명 장비 총 수
 * @param on           점등 상태 장비 수
 * @param off          소등 상태 장비 수
 * @param operating    통신되어 상태를 읽은 장비 수(정상 + 알람)
 * @param commNormal   통신 정상 장비 수
 * @param commAbnormal 통신 비정상 장비 수
 * @param alarm        알람 장비 수
 */
public record LightingOperationStatusResponse(
        long total,
        long on,
        long off,
        long operating,
        long commNormal,
        long commAbnormal,
        long alarm
) {
    public static LightingOperationStatusResponse from(List<LightingReading> readings) {
        long total = readings.size();
        long on = readings.stream().filter(r -> r.state() == LightingState.ON).count();
        long off = readings.stream().filter(r -> r.state() == LightingState.OFF).count();
        long commNormal = readings.stream().filter(r -> r.commStatus() == CommStatus.NORMAL).count();
        long commAbnormal = readings.stream().filter(r -> r.commStatus() == CommStatus.ABNORMAL).count();
        long alarm = readings.stream().filter(r -> r.commStatus() == CommStatus.ALARM).count();
        long operating = commNormal + alarm;
        return new LightingOperationStatusResponse(total, on, off, operating, commNormal, commAbnormal, alarm);
    }
}
