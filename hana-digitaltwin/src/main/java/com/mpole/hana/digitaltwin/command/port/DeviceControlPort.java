package com.mpole.hana.digitaltwin.command.port;

/**
 * 디바이스 제어 명령을 외부(연계사)로 전달하는 아웃바운드 포트.
 * <p>현재 {@link XnDeviceControlAdapter} 가 XN 제어 API(RestClient) 로 구현한다.
 * 연계사 교체 시 이 인터페이스만 다른 어댑터로 교체하면 서비스/컨트롤러 변경이 없다.</p>
 */
public interface DeviceControlPort {

    /**
     * 단일 포인트 제어 명령 전송.
     *
     * @param refDeviceCode 외부 deviceCode (tbl_device_point.ref_device_code)
     * @param refPointCode  외부 pointCode (tbl_device_point.ref_point_code)
     * @param value         전송값 (예: "1"/"0")
     * @return 연계사가 명령을 수락하면 true
     */
    boolean dispatch(String refDeviceCode, String refPointCode, String value);
}
