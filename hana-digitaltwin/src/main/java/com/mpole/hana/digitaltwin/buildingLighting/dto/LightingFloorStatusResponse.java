package com.mpole.hana.digitaltwin.buildingLighting.dto;

import com.mpole.hana.digitaltwin.buildingLighting.domain.CommStatus;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingReading;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingState;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * UI 층 선택 패널의 층별 점/소등 요약 행 (1F ~ B5F 등).
 */
public record LightingFloorStatusResponse(
        Long floorId,
        String floorName,
        Integer floorNum,
        /** 층 표시 정렬 순서(0 = 최하층). 목록은 이미 이 순서로 정렬돼 있다. */
        Integer floorSortOrder,
        long deviceCount,
        long zoneCount,
        long onCount,
        long offCount,
        LightingState state,
        CommStatus commStatus
) {
    /** 같은 floorId 로 묶인 readings 그룹을 한 행으로 집계 (group 은 비어 있지 않다고 가정) */
    public static LightingFloorStatusResponse aggregate(List<LightingReading> group) {
        LightingReading any = group.get(0);
        long deviceCount = group.size();
        long onCount = group.stream().filter(r -> r.state().isOn()).count();
        long offCount = group.stream().filter(r -> r.state().isOff()).count();
        long zoneCount = group.stream()
                .map(LightingReading::zoneId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .size();

        LightingState state = onCount > 0 ? LightingState.ON
                : (offCount > 0 ? LightingState.OFF : LightingState.UNKNOWN);

        CommStatus comm = group.stream()
                .map(LightingReading::commStatus)
                .reduce(CommStatus.NORMAL, CommStatus::worse);

        return new LightingFloorStatusResponse(
                any.floorId(), any.floorName(), any.floorNum(), any.floorSortOrder(),
                deviceCount, zoneCount, onCount, offCount, state, comm);
    }
}
