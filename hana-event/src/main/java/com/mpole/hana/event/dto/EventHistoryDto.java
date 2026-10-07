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
 *   <li>{@code {event_code}} 또는 {@code EVENT} — {@link #event} : 이벤트 발생. 규칙에 event_code 가 있으면 그 값
 *       (예 WC_CLEAN), 없으면 "EVENT". eventId 는 정본 PK(UUIDv7)와 동일하며 클라이언트는 이 id 로 확인(ack)·완료 보고를 한다.</li>
 *   <li>{@code {event_code}_CLEAR} 또는 {@code EVENT_CLEAR} — {@link #clear} : 이벤트 해소(정상 복귀). 발생 때와 같은 eventId 라
 *       클라이언트가 어느 알람을 내릴지 바로 찾을 수 있다.</li>
 *   <li>{@code {event_code}_ACK} 또는 {@code EVENT_ACK} — {@link #ack} : 관제자 확인. 모든 세션에 나간다(2026-10-02).</li>
 * </ul>
 * <p>판별 규칙(클라이언트): {@code data != null} 이면 장비 값, {@code event != null} 이면 발생(같은 eventId 가 또 오면 재활성),
 * {@code clear != null} 이면 해소, {@code ack != null} 이면 확인. eventType 문자열은 종류 분기용이지 봉투 형태 판별용이 아니다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record EventHistoryDto(
        String eventType,
        UUID eventId,
        // isProcessed 는 2026-10-01 삭제 — 항상 false 였고 읽는 곳이 없었다(구 tbl_device_event_history 잔재).
        // "처리됨" 은 tbl_event.acked_at(관제자 확인) 하나가 담당한다.
        DeviceBundle data,
        EventOccurrence event,
        EventClear clear,
        EventAck ack
) {

    public static final String TYPE_DEVICE = "DEVICE";
    public static final String TYPE_EVENT = "EVENT";
    public static final String TYPE_EVENT_CLEAR = "EVENT_CLEAR";

    /**
     * 발생 봉투. {@code eventType} 은 규칙의 {@code event_code}(예 WC_CLEAN) 이고, 규칙에 코드가 없으면 "EVENT" 다
     * (2026-10-01 — 클라이언트가 이벤트 종류별로 화면을 분기할 수 있게). 본문 {@code event.eventCode} 에도 같은 값이 있다.
     */
    public static EventHistoryDto occurrence(EventOccurrence ev) {
        return EventHistoryDto.builder()
                .eventType(ev.eventCode() != null && !ev.eventCode().isBlank() ? ev.eventCode() : TYPE_EVENT)
                .eventId(ev.eventId())
                .event(ev)
                .build();
    }

    /**
     * 해소 봉투. {@code eventType} 은 "{event_code}_CLEAR"(예 WC_CLEAN_CLEAR), 코드가 없으면 "EVENT_CLEAR".
     * 발생 때 eventType 으로 분기한 클라이언트가 같은 접두어로 해소도 짝지을 수 있게 한다. 본문 {@code clear.eventCode} 에도 같은 값.
     */
    public static EventHistoryDto cleared(EventClear ev) {
        String base = ev.eventCode() != null && !ev.eventCode().isBlank() ? ev.eventCode() : TYPE_EVENT;
        return EventHistoryDto.builder()
                .eventType(base + "_CLEAR")
                .eventId(ev.eventId())
                .clear(ev)
                .build();
    }

    /**
     * 확인 봉투(2026-10-02). {@code eventType} 은 "{event_code}_ACK", 코드가 없으면 "EVENT_ACK".
     * 어느 화면이 확인했든 모든 세션에 나가므로 다른 화면도 같은 알람을 닫는다.
     */
    public static EventHistoryDto acked(EventAck ack) {
        String base = ack.eventCode() != null && !ack.eventCode().isBlank() ? ack.eventCode() : TYPE_EVENT;
        return EventHistoryDto.builder()
                .eventType(base + "_ACK")
                .eventId(ack.eventId())
                .ack(ack)
                .build();
    }
}
