package com.mpole.hana.digitaltwin.sop.dto;

import com.mpole.hana.digitaltwin.event.entity.EventDisplayType;
import com.mpole.hana.digitaltwin.sop.entity.SopButtonActionType;
import com.mpole.hana.digitaltwin.sop.entity.SopInstanceStatus;
import com.mpole.hana.digitaltwin.sop.entity.SopItemType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class SopRuntimeDtos {

    private SopRuntimeDtos() {
    }

    public record SopRuntimeButtonDto(
            Long buttonId,
            String buttonKey,
            String label,
            String style,
            SopButtonActionType actionType
    ) {
    }

    public record SopRuntimeItemDto(
            Long itemId,
            Integer sortOrder,
            SopItemType itemType,
            String label,
            String description,
            Map<String, Object> config,
            Boolean required,
            Boolean completed,
            List<SopRuntimeButtonDto> buttons
    ) {
    }

    public record SopRuntimeStepDto(
            Long stepId,
            Integer stepNo,
            String title,
            String subtitle,
            Boolean showCompleteButton,
            String stepStatus,
            List<SopRuntimeItemDto> items
    ) {
    }

    public record SopRuntimePayload(
            Long instanceId,
            String title,
            SopInstanceStatus status,
            String resultStatus,
            Integer currentStepNo,
            OffsetDateTime timeoutAt,
            List<SopRuntimeStepDto> steps
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

    public record EventDisplayResponse(
            Long eventId,
            EventDisplayType displayType,
            Map<String, Object> event,
            Map<String, Object> map,
            Map<String, Object> cctv,
            SopRuntimePayload sop,
            SimpleAlarmPayload alarm
    ) {
    }

    public record ButtonClickRequest(
            Long actorUserId,
            Map<String, Object> clientMeta
    ) {
    }

    public record ButtonClickResponse(
            SopInstanceStatus instanceStatus,
            String resultStatus,
            Integer currentStepNo,
            Boolean closed
    ) {
    }
}
