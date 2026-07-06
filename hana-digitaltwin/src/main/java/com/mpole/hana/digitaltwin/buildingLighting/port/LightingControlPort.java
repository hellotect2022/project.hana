package com.mpole.hana.digitaltwin.buildingLighting.port;

/**
 * 조명 제어 명령을 외부(연계/BMS)로 전달하는 아웃바운드 포트.
 * <p>현재는 {@link StubLightingControlPort} 가 no-op 으로 구현. 연계 서비스 REST API 가 확정되면
 * RestClient 기반 어댑터로 이 인터페이스만 교체하면 된다(서비스/컨트롤러 변경 불필요).
 */
public interface LightingControlPort {

    /**
     * 제어 명령 전송.
     *
     * @return 연계 서비스가 명령을 수락하면 true
     */
    boolean dispatch(LightingControlMessage message);
}
