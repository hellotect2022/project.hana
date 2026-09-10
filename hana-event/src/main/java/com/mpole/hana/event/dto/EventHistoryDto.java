package com.mpole.hana.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.EventClear;
import com.mpole.hana.common.dto.EventOccurrence;
import lombok.Builder;

import java.util.UUID;

/**
 * 웹소켓 발행 봉투. {@code eventType} 이 어느 필드가 채워져 있는지를 가리킨다.
 *
 * <ul>
 *   <li>{@code DEVICE} — {@link #data} : 장비 포인트 값 번들(현재값 스트림)</li>
 *   <li>{@code EVENT} — {@link #event} : 이벤트 발생. eventId 는 정본 PK(UUIDv7)와 동일하며
 *       클라이언트는 이 id 로 확인(ack)·액션 완료 보고를 한다.</li>
 *   <li>{@code EVENT_CLEAR} — {@link #clear} : 이벤트 해소(정상 복귀). 발생 때와 같은 eventId 라
 *       클라이언트가 어느 알람을 내릴지 바로 찾을 수 있다.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record EventHistoryDto(
        String eventType,
        UUID eventId,
        Boolean isProcessed,
        DeviceBundle data,
        EventOccurrence event,
        EventClear clear
) {

    public static final String TYPE_DEVICE = "DEVICE";
    public static final String TYPE_EVENT = "EVENT";
    public static final String TYPE_EVENT_CLEAR = "EVENT_CLEAR";

    public static EventHistoryDto occurrence(EventOccurrence ev) {
        return EventHistoryDto.builder()
                .eventType(TYPE_EVENT)
                .eventId(ev.eventId())
                .isProcessed(false)
                .event(ev)
                .build();
    }

    public static EventHistoryDto cleared(EventClear ev) {
        return EventHistoryDto.builder()
                .eventType(TYPE_EVENT_CLEAR)
                .eventId(ev.eventId())
                .isProcessed(false)
                .clear(ev)
                .build();
    }
}
