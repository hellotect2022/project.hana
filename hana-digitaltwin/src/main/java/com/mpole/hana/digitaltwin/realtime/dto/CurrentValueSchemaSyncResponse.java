package com.mpole.hana.digitaltwin.realtime.dto;

import java.util.List;

/**
 * 현재값 스키마 정리 결과 (2026-10-26).
 *
 * @param applied        false = 미리보기(아무것도 안 바꿈), true = 실제로 지움
 * @param updatedDevices 실제로 points 를 고친 장비 수(미리보기는 0)
 * @param removedPoints  지울(지운) 포인트 원소 수 = removedTags 의 deviceCount 합
 * @param removedTags    카테고리·태그별 지울(지운) 장비 수
 */
public record CurrentValueSchemaSyncResponse(
        boolean applied,
        int updatedDevices,
        int removedPoints,
        List<RemovedTag> removedTags
) {
    public record RemovedTag(Long categoryId, String categoryName, String tagName, int deviceCount) {
    }
}
