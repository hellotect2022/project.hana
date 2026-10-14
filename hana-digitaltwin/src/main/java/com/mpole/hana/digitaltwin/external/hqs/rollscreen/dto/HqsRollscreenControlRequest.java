package com.mpole.hana.digitaltwin.external.hqs.rollscreen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 전동롤스크린 제어 요청 — {@code POST /api/v1/hqs/rollscreen/control}
 *
 * @param bldgId      건물 아이디. 대상 건물이 하나뿐이라 검증하지 않는다
 * @param florId      층 아이디. 층 번호({@code 10})·층 이름({@code 10F}) 둘 다 받는다
 * @param groupId     제어기 {@code device_name}(예: {@code MT.F010.Z000}).
 *                    <b>비우면 그 층 전체를 일괄 제어한다.</b>
 * @param openPercent 개방률. <b>100 = 완전열림 / 0 = 닫힘</b>, 20 단위
 *                    (제어 포인트가 {@code RANGE 0~100 step 20} 이라 그 외 값은 거부된다)
 */
public record HqsRollscreenControlRequest(
        @NotBlank String bldgId,
        @NotBlank String florId,
        @NotBlank String groupId,
        @NotNull @Min(0) @Max(100) Integer openPercent
) {
}
