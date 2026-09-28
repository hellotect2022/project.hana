package com.mpole.hana.digitaltwin.external.hqs.charger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 충전구(connector) 1건.
 *
 * <p><b>원 명세 대비 3개 필드를 뺐다</b> — 현재 수집되지 않는 값이라 내려도 항상 null 이기 때문이다.</p>
 * <ul>
 *   <li>{@code connectorId} — 충전기당 충전구를 구분하는 식별자가 우리 쪽에 없다</li>
 *   <li>{@code connectorType} — DC_COMBO / AC_3PHASE 등 커넥터 규격 정보를 수집하지 않는다</li>
 *   <li>{@code remainingTime} — 남은 시간 포인트가 없다</li>
 * </ul>
 *
 * <p>배열 구조는 <b>일부러 유지</b>했다. 나중에 XN 에서 충전구별 포인트를 받게 되면
 * 필드만 채우면 되고 HQS 쪽 파서를 다시 고치지 않아도 된다.
 * 지금은 충전기 1대당 이 항목이 <b>정확히 1개</b> 들어간다.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HqsConnectorItem {

    /** Available(대기) · Charging(충전중) · Faulted(고장) */
    private String status;

    /** 상태 포인트의 최종 수집 시각 */
    private OffsetDateTime updatedAt;
}
