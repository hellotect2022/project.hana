package com.mpole.hana.digitaltwin.integration.emblaze.hqs.charger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 충전기 1대.
 *
 * @param chargerId   우리 {@code device_key} (예: {@code ELEC_EV_DCC_01_29})
 * @param chargerName 우리 {@code device_name}
 * @param type        {@code DC_FAST}(급속) · {@code AC_SLOW}(완속) — 카테고리 코드 DCC/ACC 에서 파생
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HqsChargerItem {

    private String chargerId;
    private String chargerName;
    private String type;
    private List<HqsConnectorItem> connectors;
}
