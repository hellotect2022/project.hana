package com.mpole.hana.digitaltwin.integration.emblaze.hqs.charger;

/**
 * 충전기 상태 포인트({@code st}) 원본값 → HQS 상태값 매핑.
 *
 * <p>XN 이 내려주는 {@code st} 값 정의(포인트 설명에 기재):</p>
 * <pre>
 *   0 미연결 · 1 충전대기 · 2 충전중 · 3 점검중 · 4 실험중 · 5 운영중지 · 6 전원off · 7 통신불량
 * </pre>
 *
 * <p>HQS 는 {@code Available}(대기) · {@code Charging}(충전중) · {@code Faulted}(고장) 세 가지만 받는다.
 * 위 8개를 셋으로 접어야 해서 <b>해석이 들어간다</b> — 아래 매핑이 그 판단이다.</p>
 */
public final class ChargerStatus {

    public static final String AVAILABLE = "Available";
    public static final String CHARGING = "Charging";
    public static final String FAULTED = "Faulted";

    private ChargerStatus() {
    }

    /**
     * {@code st} 원본값을 HQS 상태로 바꾼다.
     *
     * <p>판단 근거:</p>
     * <ul>
     *   <li>{@code 1 충전대기} → Available — 유일하게 "쓸 수 있는" 상태다</li>
     *   <li>{@code 2 충전중} → Charging</li>
     *   <li>{@code 3 점검중}·{@code 6 전원off}·{@code 7 통신불량} → Faulted — 쓸 수 없고 조치가 필요하다</li>
     *   <li>{@code 0 미연결}·{@code 4 실험중}·{@code 5 운영중지} → Faulted —
     *       "고장"은 아니지만 <b>이용 불가</b>다. HQS 에 Available 로 보내면 이용자가 헛걸음한다</li>
     *   <li>값이 없거나 해석 불가 → Faulted — 상태를 모를 때 사용 가능으로 보내는 쪽이 더 위험하다</li>
     * </ul>
     */
    public static String fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return FAULTED;
        }
        return switch (raw.trim()) {
            case "1" -> AVAILABLE;
            case "2" -> CHARGING;
            default -> FAULTED;
        };
    }
}
