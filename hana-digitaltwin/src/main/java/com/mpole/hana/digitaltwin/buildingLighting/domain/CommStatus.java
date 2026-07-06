package com.mpole.hana.digitaltwin.buildingLighting.domain;

/**
 * 조명 장비 통신 상태. UI "조명제어 운용현황"의 통신정상/통신비정상/알람에 대응.
 * <p>심각도 순서: {@link #ALARM} &gt; {@link #ABNORMAL} &gt; {@link #NORMAL}.
 */
public enum CommStatus {
    /** 최근 현재값이 갱신되고 알람 없음 */
    NORMAL(0),
    /** 현재값이 없거나 오래되어 통신 불가로 간주 */
    ABNORMAL(1),
    /** 포인트 중 alarmYn = Y 가 존재 */
    ALARM(2);

    private final int severity;

    CommStatus(int severity) {
        this.severity = severity;
    }

    /** 그룹(구역/층) 대표 통신상태 — 더 심각한 쪽을 채택 */
    public CommStatus worse(CommStatus other) {
        return other != null && other.severity > this.severity ? other : this;
    }
}
