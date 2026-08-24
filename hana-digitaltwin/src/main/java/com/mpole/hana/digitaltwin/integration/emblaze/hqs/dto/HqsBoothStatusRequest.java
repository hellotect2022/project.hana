package com.mpole.hana.digitaltwin.integration.emblaze.hqs.dto;

import jakarta.validation.constraints.NotBlank;

// 화장실 재실 정보 요청
public record HqsBoothStatusRequest(
        @NotBlank String bldgId,
        @NotBlank String florId,
        String wcId,
        String gender
) {
}
