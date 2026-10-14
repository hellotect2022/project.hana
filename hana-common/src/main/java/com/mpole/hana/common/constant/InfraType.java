package com.mpole.hana.common.constant;

/**
 * 인프라 노드 유형(tbl_device_infra.infra_type).
 * 중계기 외 NVR/VMS/미들웨어 등도 같은 마스터·상속 메커니즘을 재사용한다.
 */
public enum InfraType {
    REPEATER("중계기"),
    NVR("NVR"),
    VMS("VMS"),
    MIDDLEWARE("미들웨어 서버"),
    ETC("기타");

    private final String description;

    InfraType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
