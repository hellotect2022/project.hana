package com.mpole.hana.digitaltwin.command.exception;

/**
 * 디바이스 제어 명령이 외부(XN) 연계로 정상 중계되지 못했을 때 던진다.
 *
 * <p>{@code GlobalExceptionHandler} 가 이를 502(BAD_GATEWAY) + errorCode "DEVICE_CONTROL_FAILED"
 * 로 변환하여, 최종 API 응답 자체가 실패(success=false)로 내려가게 한다.</p>
 */
public class DeviceControlException extends RuntimeException {

    public DeviceControlException(String message) {
        super(message);
    }
}
