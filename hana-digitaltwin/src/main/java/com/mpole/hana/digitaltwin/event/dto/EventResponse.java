package com.mpole.hana.digitaltwin.event.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mpole.hana.digitaltwin.event.entity.EventEffect;
import com.mpole.hana.digitaltwin.event.entity.Event;
import com.mpole.hana.digitaltwin.event.entity.EventStatus;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 이벤트 단건 응답.
 *
 * <p>상태는 두 축(active / acked)을 <b>둘 다</b> 내려준다. 클라이언트가 "지금 떠 있는 알람"과
 * "확인 안 한 알람"을 따로 걸러야 하기 때문이다. {@code status} 는 두 축을 조합한 파생값이라
 * 화면 배지에 그대로 쓰면 된다.</p>
 */
@Builder
public record EventResponse(
        UUID eventId,
        Long ruleId,
        String ruleName,
        String eventCode,
        String level,
        String scope,
        Long deviceId,
        String deviceKey,
        Long pointId,
        String tagName,
        String pointType,
        String valueRaw,
        Double thresholdValue,
        String operator,
        String triggerValue,
        String icon,
        EventEffect effect,
        OffsetDateTime occurredAt,
        int occurrenceCount,         // 성립 횟수(재활성마다 +1)
        OffsetDateTime lastOccurredAt,
        boolean active,              // 파생값: clearedAt == null (컬럼 아님)
        OffsetDateTime clearedAt,
        String clearedValue,
        OffsetDateTime ackedAt,
        Long ackedBy,
        EventStatus status,
        boolean simulated,           // 모의 이벤트(SOP 훈련) 여부. 2026-10-07
        Long simulatedBy,
        boolean sopEnabled,          // SOP 이벤트 여부(발생 시점). true 면 확인 시 sopResult 필수. 2026-10-12
        JsonNode sopResult           // SOP 완료 클릭 기록 스냅샷(확인 때 저장, 형식 자유). 미확인이면 null
) {
    public static EventResponse from(Event h) {
        if (h == null) return null;
        return EventResponse.builder()
                .eventId(h.getEventId())
                .ruleId(h.getRuleId())
                .ruleName(h.getRuleName())
                .eventCode(h.getEventCode())
                .level(h.getLevel())
                .scope(h.getScope())
                .deviceId(h.getDeviceId())
                .deviceKey(h.getDeviceKey())
                .pointId(h.getPointId())
                .tagName(h.getTagName())
                .pointType(h.getPointType())
                .valueRaw(h.getValueRaw())
                .thresholdValue(h.getThresholdValue())
                .operator(h.getOperator())
                .triggerValue(h.getTriggerValue())
                .icon(h.getIcon())
                .effect(h.getEffect())
                .occurredAt(h.getOccurredAt())
                .occurrenceCount(h.getOccurrenceCount())
                .lastOccurredAt(h.getLastOccurredAt())
                .active(h.isActive())
                .clearedAt(h.getClearedAt())
                .clearedValue(h.getClearedValue())
                .ackedAt(h.getAckedAt())
                .ackedBy(h.getAckedBy())
                .status(h.getStatus())
                .simulated(h.isSimulated())
                .simulatedBy(h.getSimulatedBy())
                .sopEnabled(h.isSopEnabled())
                .sopResult(h.getSopResult())
                .build();
    }
}
