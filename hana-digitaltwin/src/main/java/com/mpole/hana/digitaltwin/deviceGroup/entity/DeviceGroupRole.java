package com.mpole.hana.digitaltwin.deviceGroup.entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 그룹 안에서 장비가 하는 일 — <b>두 값뿐이다.</b>
 *
 * <p>쿼리가 실제로 갈라야 하는 것이 그 둘이다. 장비의 종류(조명·센서·버튼·충전기)는
 * {@code category} 가 이미 말해주므로 role 이 다시 적지 않는다.</p>
 *
 * <pre>
 *   CONTROLLER  제어 명령을 받는 장비   MRS-CONTROL(롤스크린 제어기) · RL(화장실 조명)
 *   MONITOR     상태를 보고하는 장비    MRS(모터) · OCC(재실센서) · TB(물리버튼)
 * </pre>
 *
 * <p><b>이 enum 은 엔티티 타입이 아니다.</b> {@code DeviceGroupMember.role} 은
 * {@code String} 으로 매핑돼 있고, 여기 없는 값도 저장·조회된다. 이 목록의 역할은
 * 관리자 화면 select 를 채울 <b>기본 코드 목록</b>과 카테고리로부터의 추정뿐이다.</p>
 *
 * <p>그렇게 한 이유 — DB 컬럼은 {@code varchar(40)} 이고 CHECK 제약도 없어서
 * ({@code NoEnumCheckPostgreSQLDialect}) 값을 나중에 늘릴 수 있는데,
 * {@code @Enumerated(EnumType.STRING)} 으로 매핑하면 모르는 값을 <b>읽을 때</b>
 * {@code No enum constant} 로 터진다. 그러면 DB 만 열어둔 의미가 없다.</p>
 */
public enum DeviceGroupRole {

    /** 제어 명령을 받는 장비. */
    CONTROLLER("CONTROLLER"),

    /** 상태를 보고하는 장비. */
    MONITOR("MONITOR");

    private final String label;

    DeviceGroupRole(String label) {
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
                .filter(r -> r.name().equals(code))
                .map(DeviceGroupRole::getLabel)
                .findFirst()
                .orElse(code);
    }

    public static List<DeviceGroupRole> all() {
        return List.of(values());
    }
}
