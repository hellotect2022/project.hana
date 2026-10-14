package com.mpole.hana.event.pipeline.event.dto;

/**
 * {@code tbl_event_rule} 원본 행(레벨 제외).
 *
 * <p>digitaltwin 의 enum 에 의존하지 않는다 — scope/operator 등은 DB 에
 * enum 이름 문자열("CATEGORY", "GTE" …)로 저장돼 있고 그대로 읽어 나른다.</p>
 *
 * <p>2026-10-01 {@code condition_kind}/{@code trigger_value} 제거 — 판정 방식은 {@code operator} 하나로 정한다
 * (GTE/LTE/GT/LT = 레벨별 threshold_value 비교, EQ = 레벨별 trigger_value 일치).</p>
 */
public record RuleRow(
        Long ruleId,
        String ruleName,
        String eventCode,      // 클라이언트 분기용 이벤트 코드(nullable). 웹소켓 eventType 으로 나간다
        String scope,          // CATEGORY / DEVICE
        Long categoryId,       // scope=CATEGORY 일 때 사용(대분류/중분류/소분류 어느 것이든)
        Long deviceId,         // scope=DEVICE 일 때 사용
        String tagName,
        String pointType,      // AI / DI ...
        String operator,       // GTE / LTE / GT / LT / EQ
        int sustainSec,
        Double deadband,       // 해소 판정 여유값(nullable)
        String suppressMode    // WHILE_ACTIVE / UNTIL_ACKED
) {
}
