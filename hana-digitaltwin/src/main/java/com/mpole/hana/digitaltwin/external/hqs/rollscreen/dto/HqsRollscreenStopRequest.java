package com.mpole.hana.digitaltwin.external.hqs.rollscreen.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 전동롤스크린 정지 요청 — {@code POST /api/v1/hqs/rollscreen/stop} (2026-10-26).
 * 제어 요청({@link HqsRollscreenControlRequest})에서 {@code openPercent} 만 뺀 모양이다.
 *
 * @param bldgId  건물 아이디. 대상 건물이 하나뿐이라 검증하지 않는다
 * @param florId  층 아이디. 층 번호({@code 3})·층 이름({@code 3F}) 둘 다 받는다
 * @param groupId 구역 {@code group_code}(예: {@code RS.F003.Z013})
 */
public record HqsRollscreenStopRequest(
        @NotBlank String bldgId,
        @NotBlank String florId,
        @NotBlank String groupId
) {
}
