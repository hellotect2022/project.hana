package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.common.constant.SuppressMode;
import com.mpole.hana.digitaltwin.event.entity.RuleConditionKind;
import com.mpole.hana.digitaltwin.event.entity.RuleOperator;
import com.mpole.hana.digitaltwin.event.entity.RuleScope;
import lombok.Builder;

import java.util.List;

/**
 * 이벤트 규칙 등록/수정 요청.
 *
 * <p>시간 기반 쿨다운은 없다(2026-09-30 삭제). 중복 억제는 Active 축 + {@code suppressMode},
 * 플래핑 방지는 {@code deadband} 가 담당한다.</p>
 */
@Builder
public record EventRuleRequest(
        String ruleName,
        String description,
        RuleScope scope,
        Long categoryId,
        Long deviceId,
        String tagName,
        String pointType,
        RuleConditionKind conditionKind,
        RuleOperator operator,
        String triggerValue,
        int sustainSec,
        Double deadband,
        SuppressMode suppressMode,
        Boolean active,
        List<EventRuleLevelItem> levels
) {
}
