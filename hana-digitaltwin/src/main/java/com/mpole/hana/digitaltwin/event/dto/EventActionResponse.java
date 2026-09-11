package com.mpole.hana.digitaltwin.event.dto;

import com.mpole.hana.common.constant.ActionStatus;
import com.mpole.hana.common.constant.NotificationChannel;
import com.mpole.hana.digitaltwin.event.entity.EventAction;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 이벤트 1건에서 발행된 채널별 액션 응답.
 *
 * <p>클라이언트는 {@code channel} 로 어떤 패널·전송을 띄울지, {@code status} 로 완료 여부를 판단한다.
 * 완료 보고는 {@code POST /api/event/{eventId}/actions/{channel}/complete} 로 한다.</p>
 */
public record EventActionResponse(
        UUID actionId,
        UUID eventId,
        NotificationChannel channel,
        String channelLabel,
        ActionStatus status,
        Map<String, Object> payload,
        Map<String, Object> result,
        OffsetDateTime issuedAt,
        OffsetDateTime completedAt,
        Long completedBy,
        String errorMessage
) {
    public static EventActionResponse from(EventAction a) {
        if (a == null) return null;
        return new EventActionResponse(
                a.getActionId(),
                a.getEventId(),
                a.getChannel(),
                a.getChannel() != null ? a.getChannel().getDescription() : null,
                a.getStatus(),
                a.getPayload(),
                a.getResult(),
                a.getIssuedAt(),
                a.getCompletedAt(),
                a.getCompletedBy(),
                a.getErrorMessage()
        );
    }
}
