package com.mpole.hana.digitaltwin.command.dto;

import lombok.Builder;

/**
 * 디바이스 제어 포인트 등록/수정 요청.
 *
 * @param deviceId  대상 디바이스 ID
 * @param pointId   제어에 사용할 포인트(tbl_device_point) ID — 해당 디바이스 소속이어야 함
 * @param label     제어 버튼 표시명
 * @param onValue   ON 명령 전송값 (null 이면 기본 "1")
 * @param offValue  OFF 명령 전송값 (null 이면 기본 "0")
 * @param sortOrder 정렬 순서
 * @param active    활성 여부 (null 이면 true)
 */
@Builder
public record CommandPointRequest(
        Long deviceId,
        Long pointId,
        String label,
        String onValue,
        String offValue,
        Integer sortOrder,
        Boolean active
) {
}
