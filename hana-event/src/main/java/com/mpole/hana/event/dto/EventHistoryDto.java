package com.mpole.hana.event.dto;

import com.mpole.hana.common.dto.DeviceBundle;
import lombok.Builder;

import java.util.UUID;

@Builder
public record EventHistoryDto(
        String eventType,
        UUID eventId,
        Boolean isProcessed,
        DeviceBundle data
) {
}
