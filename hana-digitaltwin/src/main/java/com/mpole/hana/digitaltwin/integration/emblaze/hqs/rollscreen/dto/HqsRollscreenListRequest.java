package com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 전동롤스크린 목록 조회 요청 — {@code POST /api/v1/hqs/rollscreen/rollscreens/list}
 *
 * <p>명세서 표기는 {@code building_id}/{@code floor_id} 지만, 기존 HQS 연동 API
 * ({@code boothStatus} · {@code wcButton})와 <b>같은 camelCase</b> 로 받는다.
 * 한 연동에서 네이밍이 갈리면 클라이언트가 API 마다 다르게 다뤄야 한다.</p>
 *
 * @param bldgId 건물 아이디. 대상 건물이 하나뿐이라 <b>검증하지 않고 응답에 쓰지도 않는다</b>
 * @param florId 층 아이디. 층 번호({@code 10}) 와 층 이름({@code 10F}) 을 모두 받는다 —
 *               HQS 가 어느 형식으로 보낼지 확정되지 않았고, 한쪽만 받으면 형식이 어긋나는 순간
 *               <b>조용히 빈 목록</b>이 나가기 때문이다
 */
public record HqsRollscreenListRequest(
        @NotBlank String bldgId,
        @NotBlank String florId
) {
}
