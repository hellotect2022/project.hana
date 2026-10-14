package com.mpole.hana.digitaltwin.energy.dto;

import java.time.OffsetDateTime;

/**
 * 누적 포인트 1개의 금일·금월 전력량 (2026-10-12). {@code GET /api/energy/summary}.
 *
 * <p>모든 값은 카테고리 스키마 배율(scale)이 이미 적용된 값이다(저장부터 배율 적용 — event 서버가 수신 직후 변환).
 * 단위는 스키마 {@code unit}.</p>
 *
 * @param current  현재 누적값(tbl_device_current_value). 숫자가 아니거나 없으면 null
 * @param sourceAt 현재값이 실제로 갱신된 시각
 * @param stale    현재값이 15분 넘게 갱신되지 않음(통신 끊김 의심). 금일·금월은 마지막 값으로 계산된다
 * @param today    금일 = 현재값 − 오늘 00:00 기준값(KST)
 * @param week     금주 = 현재값 − 이번 주 월요일 00:00 기준값(KST)
 * @param month    금월 = 현재값 − 이달 1일 00:00 기준값(KST)
 */
public record EnergySummary(
        Long deviceId,
        String deviceName,
        String deviceKey,
        Long pointId,
        String tagName,
        String pointName,
        String unit,
        Double current,
        OffsetDateTime sourceAt,
        boolean stale,
        Period today,
        Period week,
        Period month
) {

    /**
     * 기간 사용량 = 현재값 − 기준값 (요구사항 식 그대로, 음수면 0).
     *
     * @param value        사용량. 이 포인트의 스냅샷이 하나도 없으면 null
     * @param periodStart  기간 시작(오늘 00:00 / 이달 1일 00:00, KST)
     * @param baseAt       실제로 쓴 기준 스냅샷 시각. periodStart 이하의 가장 가까운 것, 없으면 periodStart 이후의 가장 가까운 것
     * @param baseAdjusted periodStart 이하 스냅샷이 없어 이후의 가장 가까운 스냅샷을 기준으로 썼다(그 앞 사용량은 빠짐)
     */
    public record Period(Double value, OffsetDateTime periodStart, OffsetDateTime baseAt, boolean baseAdjusted) {
    }
}
