package com.mpole.hana.common.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * 알람 이벤트 kafka 계약.
 *
 * <p>hana-event 의 알람 평가엔진이 포인트 값을 정책 조건에 대해 평가하여 발행하고,
 * hana-digitaltwin(Phase4)이 alarm 토픽에서 소비/역직렬화하는 모듈 간 공유 계약이다.</p>
 *
 * <p>모듈 간 kafka 메시지 계약이므로 특정 모듈의 enum 에 의존하지 않고
 * String/기본타입만 사용한다. (예: level, scope, operator 등은 enum 명을 String 으로 담는다.)</p>
 */
@Builder(toBuilder = true)
public record AlarmEvent(
        Long alarmId,              // 정본(tbl_alarm_history) PK = 단일 eventId. 저장 후 세팅(발생 시점 null)
        Long policyId,             // 알람 정책 ID
        String policyName,         // 알람 정책 명
        String level,              // AlarmLevel 이름 (예: CAUTION/WARNING/CRITICAL)
        String scope,              // 정책 적용 범위 (CATEGORY/DEVICE)
        Long deviceId,             // 우리쪽 DB 의 deviceId
        String deviceKey,          // 디바이스 식별 키
        Long pointId,              // 우리쪽 DB 의 pointId
        String tagName,            // 포인트 태그명
        String pointType,          // 포인트 타입 (AI/DI 등)
        String conditionKind,      // 조건 종류 (THRESHOLD/TOGGLE)
        String valueRaw,           // 발생 시점 포인트 원시값
        Double thresholdValue,     // AI 임계값 (없으면 null)
        String operator,           // 비교 연산자 심볼 또는 enum 명 (>=,<= 등, 없으면 null)
        String triggerValue,       // TOGGLE 트리거값 (없으면 null)
        String displayType,        // (레거시) 표시 방식 (SIMPLE/SOP) — outputs 도입 후 파생/폴백용
        Long sopTemplateId,        // (레거시) SOP 템플릿 ID — outputs.sop.templateId 로 대체 예정
        OutputSpec outputs,        // 발생 레벨의 출력 채널 세트(NOTIFY/SOP/CCTV/SMS). 정본 스냅샷
        String icon,               // 알람 표시 아이콘
        String effect,             // 알람 표시 효과
        boolean effectEnabled,     // 효과 사용 여부
        OffsetDateTime occurredAt  // 알람 발생 시각
) {
}
