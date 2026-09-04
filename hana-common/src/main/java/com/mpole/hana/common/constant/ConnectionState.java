package com.mpole.hana.common.constant;

/**
 * 통신/연결 상태. 장비(tbl_device_current_value.connection_state)와
 * 인프라(tbl_device_infra.network_state)가 공용으로 사용한다.
 *
 * <p>외부 소스(vwDigitalTwin_02 등)의 원시 상태코드는
 * {@code hdt.infra.state-map} 설정으로 이 enum 에 매핑한다(미정의 코드는 UNKNOWN).</p>
 *
 * <p>이력: CONNECTED("연결됨") → {@link #NORMAL}("정상") 로 대체되고 {@link #ABNORMAL} 이 추가됨.
 * 기존 DB 문자열 'CONNECTED' 는 scripts/infra/2026-08-27-connection-state-4values.sql 로 이관한다.</p>
 */
public enum ConnectionState {
    /** 정상 — 통신·동작 이상 없음. */
    NORMAL("정상"),
    /** 이상 — 통신은 되지만 장애/경고 상태(부분 이상). */
    ABNORMAL("이상"),
    /** 통신끊김 — 응답 없음/연결 두절. */
    DISCONNECTED("통신끊김"),
    /** 알수없음 — 미수집/미등록/매핑되지 않은 상태코드. */
    UNKNOWN("알수없음");

    private final String description;

    ConnectionState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
