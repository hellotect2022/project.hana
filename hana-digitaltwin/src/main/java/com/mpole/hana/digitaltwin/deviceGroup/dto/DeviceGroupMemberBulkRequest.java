package com.mpole.hana.digitaltwin.deviceGroup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 그룹 멤버 <b>전체 교체</b> 요청.
 *
 * <p>개별 add/remove 대신 목록을 통째로 받는다 — CCTV 매핑
 * ({@code postCctvDeviceMapping})이 쓰는 기존 관례이고, 저장 시 배열 인덱스를
 * {@code sortOrder} 로 다시 부여할 수 있어 번호가 비거나 겹치지 않는다.</p>
 *
 * <p>빈 배열을 보내면 멤버 전체 해제다. 그룹 자체는 남는다 — 장비 0대인 그룹은 유효하다.</p>
 */
public record DeviceGroupMemberBulkRequest(

        @NotNull(message = "devices 는 필수입니다. 전체 해제는 빈 배열로 보내세요.")
        @Valid
        List<Item> devices
) {
    /**
     * @param deviceId 장비 id
     * @param role     이 그룹에서 하는 일. 생략하면 서버가 카테고리로 추정한다
     *                 (MRS-CONTROL·RL → CONTROLLER, MRS·OCC·TB → MONITOR).
     *                 알려진 두 값 외의 문자열도 그대로 저장된다.
     */
    public record Item(
            @NotNull(message = "deviceId 는 필수입니다.")
            Long deviceId,
            String role
    ) {
    }
}
