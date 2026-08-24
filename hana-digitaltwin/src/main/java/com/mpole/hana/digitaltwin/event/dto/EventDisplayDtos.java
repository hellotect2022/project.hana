package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.digitaltwin.event.entity.EventDisplayType;

import java.util.Map;

/**
 * 이벤트 표시 스펙 DTO.
 * (기존 sop.dto.SopRuntimeDtos 에서 event 도메인으로 이전 — 원래 이벤트 표시용)
 * sop 필드는 v2 SOP 런타임 재구축 시 타입 확정. 현재는 Object(null).
 */
public final class EventDisplayDtos {

    private EventDisplayDtos() {
    }

    public record EventDisplayResponse(
            Long eventId,
            EventDisplayType displayType,
            Map<String, Object> event,
            Map<String, Object> map,
            Map<String, Object> cctv,
            Object sop,
            SimpleAlarmPayload alarm
    ) {
    }

    public record SimpleAlarmPayload(
            String alarmTitle,
            String alarmName,
            String detail,
            String errorCode,
            String severity
    ) {
    }
}
