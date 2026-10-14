package com.mpole.hana.digitaltwin.external.common;

/**
 * 화장실 부스 재실의 <b>DB 표현</b> — 쓰는 쪽과 읽는 쪽이 같이 봐야 하는 어휘.
 *
 * <p>emblaze 수신이 이 값을 {@code tbl_device_current_value.points} 에 쓰고, HQS 조회가 같은
 * 값을 읽어 {@code boothStatus} 로 변환한다. 어느 한쪽에 상수를 두면 다른 쪽이 그 패키지를
 * 참조하게 되고(실제로 {@code hqs → emblaze} 가 그렇게 생겼다), 떼어내려고 문자열을 복사하면
 * 두 곳이 어긋나는 순간 <b>전 부스가 조용히 비어 보인다</b>.</p>
 *
 * <p>값이 {@code "1"}/{@code "0"} 인 이유 — {@code points[].valueRaw} 가 문자열이라 숫자가 아니라
 * 문자다. 비교는 반드시 문자열로 해야 한다.</p>
 */
public final class BoothOccupancy {

    /** 재실 포인트 태그명. */
    public static final String TAG = "occ";

    /** 재실 */
    public static final String OCCUPIED = "1";

    /** 비재실 */
    public static final String VACANT = "0";

    private BoothOccupancy() {
    }
}
