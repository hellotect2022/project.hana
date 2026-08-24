package com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HqsWcBoothItem {

    private String wcBoothId;
    /** 외부 스펙: "0", "1" 등 문자열 코드 */
    private String boothStatus;
}
