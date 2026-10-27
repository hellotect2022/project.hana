package com.mpole.hana.digitaltwin.realtime.dto;

import java.util.List;

/**
 * 등록 포인트 스키마 정리 결과 (2026-10-26) — tbl_device_point 에서 카테고리 스키마에 없는 태그의 포인트.
 *
 * @param applied       false = 미리보기(아무것도 안 바꿈), true = 실제로 지움
 * @param deletedPoints 지울(지운) 포인트 수 — 제어 포인트로 쓰는 포인트는 빠진다
 * @param skippedPoints 제어 포인트로 쓰고 있어 건너뛴(건너뛸) 포인트 수
 * @param points        스키마에 없는 포인트 목록(실행 결과면 실행 직전 기준)
 * @param currentValue  실행 시 같은 트랜잭션에서 함께 정리한 현재값 결과. 미리보기는 null
 */
public record PointSchemaSyncResponse(
        boolean applied,
        int deletedPoints,
        int skippedPoints,
        List<OrphanPoint> points,
        CurrentValueSchemaSyncResponse currentValue
) {
    /**
     * @param commandPointCount 이 포인트를 쓰는 제어 포인트 수 — 1 이상이면 지우지 않는다(FK)
     * @param eventCount        이 포인트로 발생한 이벤트 수 — 이력은 id 만 들고 있어 지워도 남는다(참고)
     * @param deletable         commandPointCount == 0
     */
    public record OrphanPoint(
            Long pointId,
            Long deviceId,
            String deviceName,
            Long categoryId,
            String categoryName,
            String tagName,
            String pointKey,
            int commandPointCount,
            int eventCount,
            boolean deletable
    ) {
    }
}
