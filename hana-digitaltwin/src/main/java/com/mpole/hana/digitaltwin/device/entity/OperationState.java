package com.mpole.hana.digitaltwin.device.entity;

/**
 * 장비 동작상태. (산정 로직은 추후 — 현재는 UNKNOWN 기본, 상태값 전달용 컬럼만 확보.)
 */
public enum OperationState {
    RUNNING("동작"),
    STOPPED("정지"),
    FAULT("고장"),
    UNKNOWN("알수없음");

    private final String description;

    OperationState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
