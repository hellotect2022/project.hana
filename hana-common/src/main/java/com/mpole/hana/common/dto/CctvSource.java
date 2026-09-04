package com.mpole.hana.common.dto;

/**
 * CCTV 출력 채널의 카메라 소스 결정 방식. 각 상수가 표시용 설명을 소유(클라이언트 메타 동기화용).
 */
public enum CctvSource {
    /** 발생 장비(deviceId) 기준 tbl_device_cctv_mapping 매핑으로 자동 선정(기본). */
    DEVICE_MAPPING("발생 장비 주변 자동"),
    /** 명시적으로 지정한 cctv id 목록 사용. */
    EXPLICIT("직접 지정");

    private final String description;

    CctvSource(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
