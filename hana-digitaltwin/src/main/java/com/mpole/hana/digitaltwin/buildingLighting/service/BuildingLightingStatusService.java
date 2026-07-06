package com.mpole.hana.digitaltwin.buildingLighting.service;

import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingReading;
import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingFloorStatusResponse;
import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingOperationStatusResponse;
import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingZoneStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 건물조명 조회(집계) 서비스 — 운용현황, 구역별 현황, 층별 현황.
 */
@Service
@RequiredArgsConstructor
public class BuildingLightingStatusService {

    private static final Comparator<LightingReading> BY_FLOOR_THEN_ZONE = Comparator
            .comparing(LightingReading::floorNum, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LightingReading::zoneId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final BuildingLightingReader reader;

    /** 패널 3: 건물조명 전체 운용현황 */
    @Transactional(readOnly = true)
    public LightingOperationStatusResponse getOperationStatus() {
        return LightingOperationStatusResponse.from(reader.readAll());
    }

    /** 전경: 전체 구역별 현황 */
    @Transactional(readOnly = true)
    public List<LightingZoneStatusResponse> getZoneStatuses() {
        return aggregateZones(reader.readAll());
    }

    /** 층 선택: 특정 층의 구역별 현황 */
    @Transactional(readOnly = true)
    public List<LightingZoneStatusResponse> getZoneStatusesByFloor(Long floorId) {
        return aggregateZones(reader.readByFloor(floorId));
    }

    /** 층 선택 패널: 층별 점/소등 요약 목록 */
    @Transactional(readOnly = true)
    public List<LightingFloorStatusResponse> getFloorStatuses() {
        List<LightingReading> readings = reader.readAll();

        Map<Long, List<LightingReading>> byFloor = new LinkedHashMap<>();
        readings.stream()
                .filter(LightingReading::placedOnFloor)
                .sorted(BY_FLOOR_THEN_ZONE)
                .forEach(r -> byFloor.computeIfAbsent(r.floorId(), k -> new java.util.ArrayList<>()).add(r));

        return byFloor.values().stream()
                .map(LightingFloorStatusResponse::aggregate)
                .toList();
    }

    private List<LightingZoneStatusResponse> aggregateZones(List<LightingReading> readings) {
        Map<Long, List<LightingReading>> byZone = new LinkedHashMap<>();
        readings.stream()
                .filter(LightingReading::placedInZone)
                .sorted(BY_FLOOR_THEN_ZONE)
                .forEach(r -> byZone.computeIfAbsent(r.zoneId(), k -> new java.util.ArrayList<>()).add(r));

        return byZone.values().stream()
                .map(LightingZoneStatusResponse::aggregate)
                .toList();
    }
}
