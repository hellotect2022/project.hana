package com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen.dto;

import lombok.Builder;

import java.util.List;

/**
 * 전동롤스크린 목록 응답 본문({@code data}).
 *
 * @param floorId   층 아이디 — 우리 {@code floor_num} 을 문자열로
 * @param floorName 층 이름 — 우리 {@code floor_name}({@code 10F})
 * @param groupList 그 층의 롤스크린 목록. 없으면 빈 배열(null 아님)
 */
@Builder
public record HqsRollscreenListData(
        String floorId,
        String floorName,
        List<HqsRollscreenGroupItem> groupList
) {
}
