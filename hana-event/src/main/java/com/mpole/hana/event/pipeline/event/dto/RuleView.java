package com.mpole.hana.event.pipeline.event.dto;

import com.mpole.hana.common.constant.SuppressMode;
import com.mpole.hana.common.dto.OutputSpec;

import java.util.List;

/**
 * 평가에 필요한 규칙({@code tbl_event_rule} + {@code _level})의 불변 뷰.
 *
 * <p>모듈 간 계약(EventOccurrence)과 마찬가지로 digitaltwin enum 에 의존하지 않고
 * String 으로 값을 보관한다(operator/level/scope).</p>
 *
 * <p><b>판정 방식은 operator 하나로 정한다</b>(2026-10-01, condition_kind 폐기):</p>
 * <ul>
 *   <li>GTE/LTE/GT/LT — 포인트 값(숫자)을 레벨별 {@link RuleLevelView#thresholdValue} 와 비교. deadband 적용</li>
 *   <li>EQ — 포인트 값이 레벨별 {@link RuleLevelView#triggerValue} 집합(쉼표 구분) 중 하나와 일치. deadband 무관</li>
 * </ul>
 * 어느 쪽이든 성립한 레벨 중 최고 심각도를 채택한다.
 *
 * <p><b>cooldownSec 가 없는 이유</b> — Active 축이 "활성 이벤트가 있으면 무시"로 중복을
 * 구조적으로 막으므로 시간 기반 억제가 필요 없다. 대신 해소 판정에 {@link #deadband} 를 쓴다.</p>
 *
 * @param levels displayOrder 순으로 정렬된 등급 목록
 */
public record RuleView(
        Long ruleId,
        String ruleName,
        String eventCode,      // 클라이언트 분기용 이벤트 코드(nullable). 발생/해소 스냅샷과 웹소켓 eventType 으로 나간다
        String scope,          // CATEGORY / DEVICE
        String tagName,
        String pointType,      // AI / DI ...
        String operator,       // GTE / LTE / GT / LT / EQ
        int sustainSec,
        Double deadband,       // 해소 판정 여유값(nullable=0) — 임계 근처 요동으로 발생/해소가 반복되는 플래핑 방지
        String suppressMode,   // WHILE_ACTIVE(기본) / UNTIL_ACKED
        List<RuleLevelView> levels
) {

    /** 일치(EQ) 규칙인지 — 레벨별 triggerValue 로 판정한다. */
    public boolean isEquality() {
        return "EQ".equals(operator);
    }

    /** 확인(ack) 전까지는 해소돼도 재발생시키지 않는 모드인지. */
    public boolean suppressUntilAcked() {
        return SuppressMode.UNTIL_ACKED.name().equals(suppressMode);
    }

    /**
     * 사건형(suppress_mode=NONE)인지 — 활성맵·sustain·deadband 를 타지 않고,
     * 조건이 성립한 메시지 1건을 이벤트 1건(active=false)으로 바로 기록한다.
     */
    public boolean isStateless() {
        return SuppressMode.NONE.name().equals(suppressMode);
    }

    /** null 을 0 으로 정규화한 해소 여유값. */
    public double deadbandOrZero() {
        return deadband == null ? 0.0d : deadband;
    }

    /**
     * 규칙 등급 불변 뷰.
     */
    public record RuleLevelView(
            String level,          // CAUTION / WARNING / CRITICAL
            Double thresholdValue, // 비교 연산자용 임계값(nullable)
            String triggerValue,   // EQ 용 매칭값 집합, 쉼표 구분(nullable)
            String icon,
            String effect,
            int displayOrder,
            OutputSpec outputs     // 이 등급 발생 시 출력 채널 세트(nullable)
    ) {
    }
}
