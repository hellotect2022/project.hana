package com.mpole.hana.digitaltwin.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 표현정책 등록 요청 (Zone × systemType 당 1개) */
public record ZoneDisplayPolicyRequest(
        @NotNull(message = "zoneId 는 필수입니다.") Long zoneId,
        @NotBlank(message = "systemType 은 필수입니다.") String systemType,
        @Size(max = 200) String displayName,
        @Size(max = 16) String colorCode,
        Integer opacity,
        Boolean active,
        Boolean evacuationAutoShow
) {
}
