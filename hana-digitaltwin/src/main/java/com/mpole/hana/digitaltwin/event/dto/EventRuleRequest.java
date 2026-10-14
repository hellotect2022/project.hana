package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.common.constant.SuppressMode;
import com.mpole.hana.digitaltwin.event.entity.RuleOperator;
import com.mpole.hana.digitaltwin.event.entity.RuleScope;
import lombok.Builder;

import java.util.List;

/**
 * 이벤트 규칙 등록/수정 요청.
 *
 * <p>판정 방식은 {@code operator} 하나로 정한다(2026-10-01 condition_kind/trigger_value 폐기):
 * GTE/LTE/GT/LT 는 레벨별 {@code thresholdValue}, EQ 는 레벨별 {@code triggerValue} 를 읽는다.</p>
 *
 * <p>시간 기반 쿨다운은 없다(2026-09-30 삭제). 중복 억제는 Active 축 + {@code suppressMode},
 * 플래핑 방지는 {@code deadband} 가 담당한다.</p>
 */
@Builder
public record EventRuleRequest(
        String ruleName,
        String eventCode,       // 클라이언트 분기용 이벤트 코드(선택). 웹소켓 eventType 으로 나간다
        String description,
        RuleScope scope,
        Long categoryId,
        Long deviceId,
        String tagName,
        String pointType,
        RuleOperator operator,
        int sustainSec,
        Double deadband,
        SuppressMode suppressMode,
        Boolean active,
        Boolean simulated,      // 모의 시나리오 전용(SOP 훈련). null=false. true 면 eventCode 필수 + SCENARIO_ 접두어 강제 (2026-10-07)
        List<EventRuleLevelItem> levels
) {
}
