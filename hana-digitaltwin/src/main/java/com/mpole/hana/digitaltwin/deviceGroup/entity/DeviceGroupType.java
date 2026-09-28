package com.mpole.hana.digitaltwin.deviceGroup.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 그룹의 용도.
 *
 * <p><b>이 enum 은 엔티티 타입이 아니다.</b> {@code DeviceGroup.groupType} 은
 * {@code String} 으로 매핑돼 있고, 여기 없는 값도 저장·조회된다. 이 목록의 역할은
 * 관리자 화면 select 를 채울 <b>기본 코드 목록</b>이다.</p>
 *
 * <p>그렇게 한 이유 — DB 컬럼은 {@code varchar(40)} 이고 CHECK 제약도 없어서
 * ({@code NoEnumCheckPostgreSQLDialect}) 용도를 나중에 늘릴 수 있는데,
 * {@code @Enumerated(EnumType.STRING)} 으로 매핑하면 모르는 값을 <b>읽을 때</b>
 * {@code No enum constant} 로 터진다. 그러면 DB 만 열어둔 의미가 없다.
 * 실제로 이 프로젝트는 같은 사고를 겪은 적이 있다 — {@code property_type='TOILET_BOOTH'}.</p>
 */
public enum DeviceGroupType {

    /** 화장실 부스 — 조명·재실센서·물리버튼. group_code 가 HQS wcBoothId 다. */
    WC_BOOTH("화장실 부스"),

    /** 전동 롤스크린 구역 — 층 전체/방위/실별. 제어기 + 모터. */
    ROLLSCREEN("롤스크린 구역"),

    /** 전기차 충전소. */
    EV_STATION("전기차 충전소"),

    /** 조명 그룹. */
    LIGHTING("조명 그룹");

    private final String label;

    DeviceGroupType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 저장 전 정규화 — 앞뒤 공백을 떼고 대문자로 맞춘다. 모르는 값도 그대로 통과시킨다. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /** 알려진 값이면 라벨, 아니면 코드 그대로. 화면이 모르는 값도 표시할 수 있게 한다. */
    public static String labelOf(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(t -> t.name().equals(code))
                .map(DeviceGroupType::getLabel)
                .findFirst()
                .orElse(code);
    }

    public static List<DeviceGroupType> all() {
        return List.of(values());
    }
}
