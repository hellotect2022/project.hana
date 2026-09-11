package com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc;

import lombok.Getter;

import java.util.Set;

/**
 * 화장실 부스 물리버튼 종류.
 *
 * <p>스위치는 <b>버튼 4개짜리 한 장치</b>다. EnOcean 메시지의 {@code status.switch_number}
 * 가 어느 버튼인지 알려준다.</p>
 *
 * <pre>
 *   0 좌상 ┐
 *   1 좌하 ┘ 좌측 = 점검요청
 *   2 우상 ┐
 *   3 우하 ┘ 우측 = 청소요청
 * </pre>
 *
 * <p>좌우 각 2개가 같은 요청으로 묶인다 — 위·아래를 구분하지 않는다.</p>
 */
@Getter
public enum WcButtonType {

    /** 점검요청 — 좌측 버튼(switch_number 0·1) */
    INSPECT(Set.of("0", "1"), "점검요청"),

    /** 청소요청 — 우측 버튼(switch_number 2·3) */
    CLEAN(Set.of("2", "3"), "청소요청");

    private final Set<String> switchNumbers;
    private final String label;

    WcButtonType(Set<String> switchNumbers, String label) {
        this.switchNumbers = switchNumbers;
        this.label = label;
    }

    /** switch_number 원본값 → 버튼 종류. 미정의 값이면 null(호출부에서 무시). */
    public static WcButtonType fromSwitchNumber(String switchNumber) {
        if (switchNumber == null || switchNumber.isBlank()) {
            return null;
        }
        String normalized = switchNumber.trim();
        for (WcButtonType type : values()) {
            if (type.switchNumbers.contains(normalized)) {
                return type;
            }
        }
        return null;
    }
}
