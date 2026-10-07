package com.mpole.hana.common.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 이벤트 발생 1건 — 모듈 간 공유 계약(정본 {@code tbl_event} 과 1:1).
 *
 * <p>hana-event 의 평가기가 포인트 값을 규칙에 대해 평가해 생성하고, DB 정본 저장과
 * WebSocket 발행에 같은 객체를 쓴다.</p>
 *
 * <p><b>eventId 는 발생 시점에 이미 채워져 있다</b>(UUIDv7). 예전처럼 DB INSERT 응답을 기다려
 * PK 를 받아올 필요가 없으므로, 저장과 발행이 서로를 기다리지 않는다. 클라이언트가 받은
 * eventId 로 그대로 확인(ack)·완료 보고를 할 수 있다.</p>
 *
 * <p>모듈 간 계약이라 특정 모듈 enum 에 의존하지 않고 String 으로 담는다
 * (level/scope/operator/conditionKind 등은 enum 이름 문자열).</p>
 */
@Builder(toBuilder = true)
public record EventOccurrence(
        UUID eventId,              // 정본 PK (UUIDv7) — 발생 시점에 생성, 웹소켓 식별자와 동일
        Long ruleId,               // 규칙 ID
        String ruleName,           // 규칙 명
        String eventCode,          // 규칙의 이벤트 코드(tbl_event_rule.event_code, 2026-10-01). 웹소켓 봉투 eventType 으로 나간다. nullable
        String level,              // CAUTION/WARNING/CRITICAL
        String scope,              // CATEGORY/DEVICE
        Long deviceId,
        String deviceKey,
        Long pointId,
        String tagName,
        String pointType,          // AI/DI 등
        String conditionKind,      // THRESHOLD/TOGGLE
        String valueRaw,           // 발생 시점 원시값
        Double thresholdValue,
        String operator,
        String triggerValue,
        OutputSpec outputs,        // 발생 레벨 출력 채널 스냅샷 — 클라이언트가 이걸 보고 팝업/SOP/CCTV 를 연다
        String icon,
        String effect,
        OffsetDateTime occurredAt,
        Integer occurrenceCount    // 같은 이벤트의 성립 횟수(2026-10-02). 최초 발생 1, 해소·미확인 상태에서 재성립할 때마다 +1
) {
}
