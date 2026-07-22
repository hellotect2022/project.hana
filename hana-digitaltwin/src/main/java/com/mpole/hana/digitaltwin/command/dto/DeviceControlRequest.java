package com.mpole.hana.digitaltwin.command.dto;

import lombok.Builder;

/**
 * 디바이스 제어 명령 요청.
 *
 * @param commandPointId 제어 포인트(tbl_device_command_point) ID
 * @param value          제어값 — "on" / "off" (대소문자 무시)
 */
@Builder
public record DeviceControlRequest(
        Long commandPointId,
        String value
) {
}
