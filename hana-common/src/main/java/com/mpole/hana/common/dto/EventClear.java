package com.mpole.hana.common.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 이벤트 해소(정상 복귀) 통지 — Active 축의 반대편.
 *
 * <p>발생만 알리고 해소를 알리지 않으면 클라이언트는 알람을 영원히 띄워두게 된다.
 * 평가기가 조건 해소를 감지하면 {@code tbl_event.active=false} 로 내리고 이 메시지를 발행한다.</p>
 *
 * <p>{@code eventId} 는 발생 때 보낸 것과 동일하므로 클라이언트가 어느 알람이 해소됐는지
 * 바로 찾을 수 있다.</p>
 */
@Builder(toBuilder = true)
public record EventClear(
        UUID eventId,
        Long ruleId,
        String eventCode,          // 규칙의 이벤트 코드(2026-10-01). 발생 때와 같은 값이라 클라이언트가 종류별로 분기할 수 있다. nullable
        Long deviceId,
        Long pointId,
        String clearedValue,       // 해소 시점 값
        OffsetDateTime clearedAt
) {
}
