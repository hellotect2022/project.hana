package com.mpole.hana.digitaltwin.buildingLighting.port;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 조명 제어 포트의 스텁 구현. 실제 전송 대신 로그만 남기고 수락(true)으로 응답한다.
 * <p>TODO: 연계(SI) 서비스의 조명제어 REST API 가 확정되면 RestClient 호출 어댑터로 교체.
 * 교체 시 이 클래스를 대체하는 새 {@link LightingControlPort} 빈을 등록하면 된다.
 */
@Slf4j
@Component
public class StubLightingControlPort implements LightingControlPort {

    @Override
    public boolean dispatch(LightingControlMessage message) {
        int deviceCount = message.deviceIds() == null ? 0 : message.deviceIds().size();
        log.info("[조명제어-STUB] 연계 서비스 전송(예정) scope={} targetId={} command={} dimming={} 대상장비={}건",
                message.scope(), message.targetId(), message.command(), message.dimmingLevel(), deviceCount);
        return true;
    }
}
