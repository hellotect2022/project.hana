package com.mpole.hana.digitaltwin.buildingLighting.service;

import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingControlCommand;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingReading;
import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingControlResultResponse;
import com.mpole.hana.digitaltwin.buildingLighting.port.LightingControlMessage;
import com.mpole.hana.digitaltwin.buildingLighting.port.LightingControlPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 건물조명 제어 서비스 — 제어 대상 장비를 산출하고 연계 포트로 명령을 전달한다.
 * <p>실제 외부 전송은 {@link LightingControlPort} 구현(현재 스텁)에 위임.
 */
@Service
@RequiredArgsConstructor
public class BuildingLightingControlService {

    private final BuildingLightingReader reader;
    private final LightingControlPort controlPort;

    /** 전 구역 점/소등 */
    @Transactional(readOnly = true)
    public LightingControlResultResponse controlAll(LightingControlCommand command) {
        List<Long> deviceIds = deviceIds(reader.readAll());
        boolean dispatched = controlPort.dispatch(LightingControlMessage.all(command, deviceIds));
        return new LightingControlResultResponse("ALL", null, command, null, deviceIds.size(), dispatched);
    }

    /** 특정 층 점/소등 */
    @Transactional(readOnly = true)
    public LightingControlResultResponse controlFloor(Long floorId, LightingControlCommand command) {
        List<Long> deviceIds = deviceIds(reader.readByFloor(floorId));
        boolean dispatched = controlPort.dispatch(LightingControlMessage.floor(floorId, command, deviceIds));
        return new LightingControlResultResponse("FLOOR", floorId, command, null, deviceIds.size(), dispatched);
    }

    /** 특정 구역 점/소등 */
    @Transactional(readOnly = true)
    public LightingControlResultResponse controlZone(Long zoneId, LightingControlCommand command) {
        List<Long> deviceIds = deviceIds(reader.readByZone(zoneId));
        boolean dispatched = controlPort.dispatch(LightingControlMessage.zone(zoneId, command, deviceIds));
        return new LightingControlResultResponse("ZONE", zoneId, command, null, deviceIds.size(), dispatched);
    }

    /** 특정 구역 디밍 조절 */
    @Transactional(readOnly = true)
    public LightingControlResultResponse dimZone(Long zoneId, Integer level) {
        List<Long> deviceIds = deviceIds(reader.readByZone(zoneId));
        boolean dispatched = controlPort.dispatch(LightingControlMessage.zoneDimming(zoneId, level, deviceIds));
        return new LightingControlResultResponse("ZONE_DIMMING", zoneId, null, level, deviceIds.size(), dispatched);
    }

    private List<Long> deviceIds(List<LightingReading> readings) {
        return readings.stream().map(LightingReading::deviceId).toList();
    }
}
