package com.mpole.hana.digitaltwin.command.dto;

/**
 * 디바이스 제어 명령 결과.
 *
 * @param success 외부(XN) 연계가 명령을 수락했는지 여부
 * @param message 결과 메시지
 */
public record DeviceControlResponse(
        boolean success,
        String message
) {
    public static DeviceControlResponse ok(String message) {
        return new DeviceControlResponse(true, message);
    }

    public static DeviceControlResponse fail(String message) {
        return new DeviceControlResponse(false, message);
    }
}
