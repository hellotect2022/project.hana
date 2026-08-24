package com.mpole.hana.digitaltwin.device.entity;

/**
 * 장비 연결상태. (산정 로직은 추후 — 현재는 UNKNOWN 기본, 상태값 전달용 컬럼만 확보.)
 */
public enum ConnectionState {
    CONNECTED("연결됨"),
    DISCONNECTED("연결끊김"),
    UNKNOWN("알수없음");

    private final String description;

    ConnectionState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
