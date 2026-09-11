package com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HqsWcBoothItem {

    /** 부스 구역의 {@code zone_code}. 예: {@code WC_4F_M01_B01} */
    private String wcBoothId;

    /**
     * 재실 상태 — {@code 0}=비어있음, {@code 1}=사용중.
     *
     * <p>HQS 명세가 정수라 {@code int} 다. 값이 아직 없는 부스(센서 메시지 미수신)는
     * {@code 0} 으로 내려간다 — 명세에 "데이터 없음"을 표현할 코드가 없다.</p>
     */
    private int boothStatus;
}
