package com.mpole.hana.digitaltwin.external.hqs.rollscreen.dto;

import lombok.Builder;

/**
 * 전동롤스크린 1대(HQS 표현으로는 "장치그룹").
 *
 * @param groupId     장치그룹 아이디 — 우리 {@code device_id}
 * @param groupName   장치그룹 이름 — 우리 {@code device_name}
 * @param openPercent 개방률. <b>100 = 완전열림 / 0 = 닫힘</b>
 */
@Builder
public record HqsRollscreenGroupItem(
        String groupId,
        String groupName,
        Integer openPercent
) {
}
