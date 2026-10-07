package com.mpole.hana.digitaltwin.event.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 알람 이력 화면 1행 — 기획서의 공통 표(명칭·위치·알람명·완료여부·발생일시) 전용 투영.
 *
 * <p>전력감시·엘리베이터·소방방재·태양광·출입통제·화장실 물리버튼 화면이 모두 같은 형식이라
 * 도메인별 응답을 만들지 않고 이 한 가지를 공유한다. 화면 제목과 필터만 달라진다.</p>
 *
 * <p><b>완료 여부는 Ack 축</b>({@code ackedAt}, 관제자 확인)으로 판정한다 — 2026-10-02 기획 확정.
 * 알람을 닫는 유일한 행위가 확인이고, 클라이언트가 기동 시 {@code completed=false} 로 미확인 목록을 복구한다.
 * 사건형(NONE, 물리버튼)은 발행과 동시에 {@code acked_at=occurred_at, acked_by=null}(시스템) 로 자동 완료되므로
 * 같은 기준으로 항상 완료다. (2026-10-01 하루 동안 {@code clearedAt} 기준이었다가 되돌림.)</p>
 *
 * @param deviceName      화면 "명칭" — 장비명(예: 화재수신기154, EV 01, E 칸)
 * @param floorName       화면 "위치" — 장비 배치 층. 배치가 없으면 null
 * @param ruleName        화면 "알람명" — 규칙 등록 시 정한 이름(예: 화재감지, 청소, SOS)
 * @param eventCode       규칙의 이벤트 코드 스냅샷(클라이언트 분기용, 예 WC_CLEAN). 없으면 null
 * @param clearedAt       해소 시각. null 이면 지금도 성립 중(활성)
 * @param occurrenceCount 성립 횟수. 해소·미확인 상태에서 재성립할 때마다 +1 (최초 1)
 * @param completed       화면 "완료 여부" — {@code ackedAt != null}
 * @param categoryName    화면 묶음(제목)용 소분류 카테고리명
 */
public record AlarmHistoryRow(
        UUID eventId,
        Long ruleId,
        String ruleName,
        String eventCode,
        String level,
        Long deviceId,
        String deviceName,
        String deviceKey,
        String floorName,
        String zoneName,
        Long categoryId,
        String categoryName,
        OffsetDateTime occurredAt,
        OffsetDateTime clearedAt,
        OffsetDateTime ackedAt,
        int occurrenceCount,
        boolean completed
) {
    /**
     * JPQL 생성자 표현식이 호출하는 생성자.
     * <p>{@code completed} 는 저장값이 아니라 {@code ackedAt} 에서 파생되므로 여기서 계산한다.</p>
     */
    public AlarmHistoryRow(UUID eventId, Long ruleId, String ruleName, String eventCode, String level,
                           Long deviceId, String deviceName, String deviceKey,
                           String floorName, String zoneName,
                           Long categoryId, String categoryName,
                           OffsetDateTime occurredAt, OffsetDateTime clearedAt, OffsetDateTime ackedAt,
                           int occurrenceCount) {
        this(eventId, ruleId, ruleName, eventCode, level, deviceId, deviceName, deviceKey,
                floorName, zoneName, categoryId, categoryName,
                occurredAt, clearedAt, ackedAt, occurrenceCount, ackedAt != null);
    }
}
