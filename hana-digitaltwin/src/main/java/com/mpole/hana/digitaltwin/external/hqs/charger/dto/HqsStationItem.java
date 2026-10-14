package com.mpole.hana.digitaltwin.external.hqs.charger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 충전소 1곳 — 우리 <b>장비 그룹</b>({@code group_type='EV_STATION'}) 하나에 대응한다.
 *
 * @param stationId   그룹의 {@code group_code}. 이름은 중복될 수 있어 키로 쓰지 않는다
 * @param stationName 그룹의 {@code group_name}
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
