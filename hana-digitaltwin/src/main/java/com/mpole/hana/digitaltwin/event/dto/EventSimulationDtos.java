package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.digitaltwin.event.entity.Event;
import com.mpole.hana.digitaltwin.event.entity.EventEffect;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 모의 이벤트(SOP 시나리오 훈련) API 의 요청/응답 — {@code /api/event/simulate}. 2026-10-07.
 *
 * <p>시나리오 = 모의 전용 규칙({@code tbl_event_rule.simulated = true})의 {@code event_code}(예 {@code SCENARIO_FIRE_DETECT}).
 * 서버는 그 코드의 모의 전용 규칙을 <b>그대로</b> 찾는다(실제 규칙으로 떨어지지 않음). 규칙의 범위·태그·레벨·outputs(SOP 템플릿)로
 * {@code tbl_event} 에 {@code simulated=true} 행을 만들고 응답으로 돌려주며, 끝은 확인(ack) 하나다(해소 단계 없음).</p>
 */
public final class EventSimulationDtos {

    private EventSimulationDtos() {
    }

    /**
     * 드롭다운 1항목(= 모의 전용 규칙 1개).
     *
     * @param eventCode Unity 가 발생 요청에 그대로 보낼 코드(규칙 event_code, SCENARIO_…)
     * @param levels    선택 가능한 레벨(심각도 오름차순). 규칙 API 와 같은 {@link EventRuleLevelItem} — 매칭값·아이콘·이펙트·
     *                  outputs(팝업/SOP 템플릿/CCTV)까지 실려 Unity 가 고르는 순간 "무엇이 뜰지" 안다. 발생 요청의 {@code level}
     *                  은 이 중 {@code level} 문자열 하나를 보내고, 생략하면 마지막(최고)이 쓰인다. (2026-10-07 outputs 포함)
     */
    public record ScenarioOption(
            String eventCode,
            Long ruleId,
            String ruleName,
            String description,
            String scope,
            Long categoryId,
            String categoryPath,
            Long deviceId,
            String deviceName,
            String tagName,
            List<EventRuleLevelItem> levels
    ) {
    }

    /**
     * 발생 요청.
     *
     * @param eventCode 모의 전용 규칙의 event_code(필수, 예 SCENARIO_FIRE_DETECT)
     * @param deviceId  대상 장비(필수)
     * @param level     CAUTION/WARNING/CRITICAL. 생략하면 규칙의 최고 심각도
     */
    public record SimulateRequest(String eventCode, Long deviceId, String level) {
    }

    /** 발생 응답. Unity 는 이것만으로 실제 알람과 같은 팝업·SOP 패널을 연다(outputs.sop.templateId → GET /api/sop/templates/{id}). */
    @Builder
    public record SimulateResponse(
            UUID eventId,
            String eventCode,
            Long ruleId,
            String ruleName,
            String level,
            Long deviceId,
            String deviceKey,
            String deviceName,
            Long pointId,
            String tagName,
            String pointType,
            String valueRaw,
            OutputSpec outputs,
            String icon,
            EventEffect effect,
            OffsetDateTime occurredAt,
            OffsetDateTime clearedAt,
            OffsetDateTime ackedAt,
            Long ackedBy,
            Long simulatedBy,
            /** 같은 장비를 다시 눌러 기존 열린 모의 행을 돌려준 경우 true. 새로 만든 건 false */
            boolean alreadyActive
    ) {
        public static SimulateResponse of(Event e, String deviceName, boolean alreadyActive) {
            return SimulateResponse.builder()
                    .eventId(e.getEventId())
                    .eventCode(e.getEventCode())
                    .ruleId(e.getRuleId())
                    .ruleName(e.getRuleName())
                    .level(e.getLevel())
                    .deviceId(e.getDeviceId())
                    .deviceKey(e.getDeviceKey())
                    .deviceName(deviceName)
                    .pointId(e.getPointId())
                    .tagName(e.getTagName())
                    .pointType(e.getPointType())
                    .valueRaw(e.getValueRaw())
                    .outputs(e.getOutputs())
                    .icon(e.getIcon())
                    .effect(e.getEffect())
                    .occurredAt(e.getOccurredAt())
                    .clearedAt(e.getClearedAt())
                    .ackedAt(e.getAckedAt())
                    .ackedBy(e.getAckedBy())
                    .simulatedBy(e.getSimulatedBy())
                    .alreadyActive(alreadyActive)
                    .build();
        }
    }

    /** 확인(ack) 응답. 이미 확인된 건이면 기존 값이 그대로 오고 {@code alreadyAcked=true}. */
    public record AckResponse(UUID eventId, OffsetDateTime ackedAt, Long ackedBy, OffsetDateTime clearedAt, boolean alreadyAcked) {
    }
}
