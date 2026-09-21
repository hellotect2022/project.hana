package com.mpole.hana.digitaltwin.buildingLighting.dto;

import com.mpole.hana.digitaltwin.buildingLighting.domain.CommStatus;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingReading;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingState;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * UI "건물조명 등록현황"의 구역(zone)별 행 — 전경 패널 및 층 선택 시 구역 목록에 사용.
 *
 * @param state   구역 대표 상태: 하나라도 점등이면 ON, 아니면 소등 있으면 OFF, 그 외 UNKNOWN
 * @param dimming 구역 평균 디밍(0~100). 디밍 보유 장비가 없으면 null
 */
public record LightingZoneStatusResponse(
        Long zoneId,
        String zoneName,
        Long floorId,
        String floorName,
        Integer floorNum,
        /** 층 표시 정렬 순서(0 = 최하층). 목록은 이미 이 순서로 정렬돼 있다. */
        Integer floorSortOrder,
        long deviceCount,
        long onCount,
        long offCount,
        LightingState state,
        Integer dimming,
        CommStatus commStatus,
        OffsetDateTime updatedAt
) {
    /** 같은 zoneId 로 묶인 readings 그룹을 한 행으로 집계 (group 은 비어 있지 않다고 가정) */
    public static LightingZoneStatusResponse aggregate(List<LightingReading> group) {
        LightingReading any = group.get(0);
        long deviceCount = group.size();
        long onCount = group.stream().filter(r -> r.state().isOn()).count();
        long offCount = group.stream().filter(r -> r.state().isOff()).count();

        LightingState state = onCount > 0 ? LightingState.ON
                : (offCount > 0 ? LightingState.OFF : LightingState.UNKNOWN);

        CommStatus comm = group.stream()
                .map(LightingReading::commStatus)
                .reduce(CommStatus.NORMAL, CommStatus::worse);

        Integer dimming = group.stream()
                .map(LightingReading::dimming)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .stream().mapToObj(avg -> (int) Math.round(avg))
                .findFirst().orElse(null);

        OffsetDateTime updatedAt = group.stream()
                .map(LightingReading::updatedAt)
                .filter(Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        return new LightingZoneStatusResponse(
                any.zoneId(), any.zoneName(), any.floorId(), any.floorName(), any.floorNum(), any.floorSortOrder(),
                deviceCount, onCount, offCount, state, dimming, comm, updatedAt);
    }
}
