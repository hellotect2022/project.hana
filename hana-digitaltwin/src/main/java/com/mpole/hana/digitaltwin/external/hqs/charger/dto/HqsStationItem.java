package com.mpole.hana.digitaltwin.external.hqs.charger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 충전소 1곳 — 우리 <b>구역(zone)</b> 하나에 대응한다.
 *
 * @param stationId   구역의 {@code zone_code}. 구역명은 층마다 중복될 수 있어 키로 쓰지 않는다
 * @param stationName 구역의 {@code zone_name}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HqsStationItem {

    private String stationId;
    private String stationName;
    private List<HqsChargerItem> chargers;
}
