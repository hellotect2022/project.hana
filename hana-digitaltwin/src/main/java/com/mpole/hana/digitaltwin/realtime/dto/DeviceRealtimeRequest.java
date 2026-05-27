package com.mpole.hana.digitaltwin.realtime.dto;

import com.mpole.hana.digitaltwin.realtime.entity.DeviceSnapshotLast;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DeviceRealtimeRequest(
        @NotEmpty(message = "deviceIds는 비어 있을 수 없습니다.")
        List<Long> deviceIds
) {
}
