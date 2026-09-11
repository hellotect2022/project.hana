package com.mpole.hana.digitaltwin.event.dto;

/**
 * 알람 이력 화면의 "데이터 선택" 드롭다운 항목.
 *
 * <p>규칙 전체 목록이 아니라 <b>실제로 발생한 적 있는 알람명</b>만 담는다 —
 * 한 번도 안 뜬 규칙까지 나오면 화면이 지저분해진다.</p>
 */
public record AlarmRuleOption(
        Long ruleId,
        String ruleName
) {
}
