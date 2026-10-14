package com.mpole.hana.digitaltwin.external.hqs.rollscreen.dto;

import lombok.Builder;

/**
 * 전동롤스크린 제어 응답 본문({@code data}).
 *
 * @param result 명세상 문자열 하나뿐이라, 전부 성공이면 {@code "success"} 를 주고
 *               일부만 성공하면 {@code "12/24"} 처럼 비율을 담는다
 *               (층 일괄 제어는 일부 실패가 가능하다)
 */
@Builder
public record HqsRollscreenControlData(
        String result
) {
    public static final String SUCCESS = "success";

    public static HqsRollscreenControlData success() {
        return new HqsRollscreenControlData(SUCCESS);
    }

    public static HqsRollscreenControlData partial(int ok, int total) {
        return new HqsRollscreenControlData(ok + "/" + total);
    }
}
