package com.mpole.hana.digitaltwin.event.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 알람 이력 화면 1행 — 기획서의 공통 표(명칭·위치·알람명·완료여부·발생일시) 전용 투영.
 *
 * <p>전력감시·엘리베이터·소방방재·태양광·출입통제·화장실 물리버튼 화면이 모두 같은 형식이라
 * 도메인별 응답을 만들지 않고 이 한 가지를 공유한다. 화면 제목과 필터만 달라진다.</p>
 *
 * <p><b>완료 여부는 Ack 축</b>({@code ackedAt})으로만 판정한다 —
 * "사람이 조치했는가"이지 "조건이 해소됐는가"({@code active})가 아니다.
 * 두 축이 독립이라 참고용으로 {@code active} 도 함께 내려준다.</p>
 *
 * @param deviceName   화면 "명칭" — 장비명(예: 화재수신기154, EV 01, E 칸)
 * @param floorName    화면 "위치" — 장비 배치 층. 배치가 없으면 null
 * @param ruleName     화면 "알람명" — 규칙 등록 시 정한 이름(예: 화재감지, 청소, SOS)
 * @param completed    화면 "완료 여부" — {@code ackedAt != null}
 * @param categoryName 화면 묶음(제목)용 소분류 카테고리명
 */
public record AlarmHistoryRow(
        UUID eventId,
        Long ruleId,
        String ruleName,
        String level,
        Long deviceId,
        String deviceName,
        String deviceKey,
        String floorName,
        String zoneName,
        Long categoryId,
        String categoryName,
        OffsetDateTime occurredAt,
        OffsetDateTime ackedAt,
        boolean active,
        boolean completed
) {
    /**
     * JPQL 생성자 표현식이 호출하는 생성자.
     * <p>{@code completed} 는 저장값이 아니라 {@code ackedAt} 에서 파생되므로 여기서 계산한다.</p>
     */
    public AlarmHistoryRow(UUID eventId, Long ruleId, String ruleName, String level,
                           Long deviceId, String deviceName, String deviceKey,
                           String floorName, String zoneName,
                           Long categoryId, String categoryName,
                           OffsetDateTime occurredAt, OffsetDateTime ackedAt, boolean active) {
        this(eventId, ruleId, ruleName, level, deviceId, deviceName, deviceKey,
                floorName, zoneName, categoryId, categoryName,
                occurredAt, ackedAt, active, ackedAt != null);
    }
}
