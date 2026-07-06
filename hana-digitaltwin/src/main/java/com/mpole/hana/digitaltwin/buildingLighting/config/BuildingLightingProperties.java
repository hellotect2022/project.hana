package com.mpole.hana.digitaltwin.buildingLighting.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 건물조명(LCS) 도메인 설정. application.yml 의 {@code app.building-lighting.*} 로 덮어쓸 수 있음.
 * <p>건물조명 카테고리에는 아직 등록 장비/스키마가 없어, ON/OFF·디밍 포인트의 tag_name 계약을
 * 여기서 정의한다. 실제 포인트 스키마 확정 시 tag 값만 조정하면 된다.
 */
@Component
@ConfigurationProperties(prefix = "app.building-lighting")
@Getter
@Setter
public class BuildingLightingProperties {

    /** 건물조명 BMS 시스템 ID (tbl_device_system.system_id). 기본 3 = 건물조명(LCS) */
    private Long systemId = 3L;

    /** 점/소등 상태 포인트의 tag_name (DI, valueRaw 0/1) */
    private String onoffTag = "onoff";

    /** 디밍(밝기) 포인트의 tag_name (AI, 0~100) */
    private String dimmingTag = "dimming";

    /** 현재값이 이 시간(초)보다 오래되면 통신 비정상으로 간주 */
    private long commStaleSeconds = 120;
}
