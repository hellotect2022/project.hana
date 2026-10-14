package com.mpole.hana.event.pipeline.event.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 재기동 복원용 이벤트 행 — 미확인인 상태형 이벤트({@code tbl_event}).
 *
 * <p>이 조회가 없으면 기동 직후 이미 떠 있는 알람이 다시 발생 처리되어 중복 통지가 나가고,
 * 해소됐지만 미확인인 알람은 억제가 풀려 확인 없이 다시 울린다(2026-10-02 B안에서 범위 확장).</p>
 *
 * @param clearedAt      null 이면 활성(①), 있으면 해소·미확인(②)
 * @param ackedAt        null 이면 미확인(복원 조회는 항상 null)
 * @param lastOccurredAt 마지막 성립 시각(재활성 포함). 복원 시 updatedAt 으로 쓴다
 * @param pointName      {@code tbl_device_point.point_display_name}. 표시용(로그 격자)
 */
public record ActiveEventRow(
        UUID eventId,
        Long ruleId,
        Long deviceId,
        Long pointId,
        String level,
        OffsetDateTime occurredAt,
        OffsetDateTime clearedAt,
        OffsetDateTime ackedAt,
        String ruleName,
        String eventCode,
        String deviceKey,
        String tagName,
        String pointName,
        OffsetDateTime lastOccurredAt
) {
}
