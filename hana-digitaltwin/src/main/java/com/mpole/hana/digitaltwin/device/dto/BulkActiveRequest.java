package com.mpole.hana.digitaltwin.device.dto;

import java.util.List;

/**
 * 장비 활성상태(active) 일괄 변경 요청.
 *
 * <ul>
 *     <li>{@code scope=ALL} — 전체 장비 대상 (categoryId/deviceIds 무시)</li>
 *     <li>{@code scope=CATEGORY} — 선택 카테고리(대·중·소) 하위 소분류에 속한 장비 대상 (categoryId 필수)</li>
 *     <li>{@code scope=DEVICES} — 지정한 deviceIds 장비 대상 (deviceIds 필수)</li>
 * </ul>
 */
public record BulkActiveRequest(
        Scope scope,
        Long categoryId,
        List<Long> deviceIds,
        Boolean active
) {
    public enum Scope {
        ALL,
        CATEGORY,
        DEVICES
    }
}
