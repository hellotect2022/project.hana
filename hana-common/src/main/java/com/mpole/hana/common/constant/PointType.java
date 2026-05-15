package com.mpole.hana.common.constant;

public enum PointType {
    // Analog 실수 (Float/Double)
    AI, // 온도, 압력 등
    AO, // 속도 제어 등
    AV, // 목표 온도 등

    // Binary 0 또는 1 (Boolean)
    BI, // 운전 상태
    BO, // 기동명령
    BV, // 제어 허가 등
    DI,
    DO,
    DV,

    // Multi-State 정수 (Integer/Enum)
    MSI, // 현재 모드
    MSO, // 모드 변경
    MSV  // 상태 설정값
}
