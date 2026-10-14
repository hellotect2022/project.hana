package com.mpole.hana.common.constant;

/**
 * 포인트 값의 <b>원천 수집 경로</b>.
 *
 * <p>어느 파이프라인이 이 포인트의 값을 공급하는지 명시한다.
 * gateway 의 뷰 스냅샷 파이프라인은 이 값을 보고 <b>자기가 소유하지 않은 포인트·장비</b>를 걸러낸다.</p>
 *
 * <h3>왜 필요한가</h3>
 * <p>gateway 는 {@code tbl_device_point} 를 전량 읽어 골격을 만들고, 기동 시 전 장비를 한 번 발행한다.
 * 그 번들에는 그 장비의 <b>모든 포인트가 값 없이도</b> 실리고, 하류(hana-event)는
 * {@code points} 배열을 통째로 교체한다. 따라서 RabbitMQ 등 다른 경로가 채우는 포인트가
 * 같은 골격에 섞이면 <b>그 값이 null 로 덮인다.</b> 이 enum 이 그 경계를 긋는다.</p>
 *
 * <h3>하위호환</h3>
 * <p>{@code null}(미지정)은 {@link #VIEW01} 로 취급한다 — 컬럼 도입 전 데이터가 전부 뷰 기반이므로,
 * 백필 없이 배포해도 기존 동작이 그대로 유지된다.</p>
 *
 * <h3>전제: 혼합 장비 금지</h3>
 * <p>한 장비의 포인트는 <b>한 소스에서만</b> 와야 한다. VIEW01 포인트와 AMQP 포인트를 한 장비에 섞으면
 * 뷰 값이 바뀔 때마다 그 장비 전체가 발행되어 AMQP 포인트가 null 로 덮인다 —
 * 이 enum 으로는 막을 수 없다(발행 단위가 장비이기 때문).</p>
 */
public enum PointDataSource {

    /** vwDigitalTwin_01 (XN 관제점 값) — gateway view01 스냅샷 파이프라인 */
    VIEW01("뷰01 (XN 관제점)"),

    /** vwDigitalTwin_02 (중계기 상태) — gateway view02 */
    VIEW02("뷰02 (중계기 상태)"),

    /** Modbus 수집 — gateway */
    MODBUS("Modbus 수집"),

    /** TCP 수집 — gateway */
    TCP("TCP 수집"),

    /** RabbitMQ(emblaze 부스 재실·물리버튼 등) — digitaltwin 이 DB 에 직접 기록 */
    AMQP("RabbitMQ 수신"),

    /** 수동 입력 */
    //MANUAL("수동 입력"),

    /** 파생·집계값(누적 카운트 등) — digitaltwin 이 계산해 직접 기록 */
    DERIVED("파생·집계값"),

    /** 제어 전용 포인트(값 수집 없음) */
    COMMAND("제어 전용");

    private final String label;

    PointDataSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 문자열 → enum. null/blank/미정의 값은 null 을 반환한다(호출부에서 하위호환 처리). */
    public static PointDataSource fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (PointDataSource source : values()) {
            if (source.name().equalsIgnoreCase(normalized)) {
                return source;
            }
        }
        return null;
    }

    /**
     * view01 스냅샷 파이프라인이 값을 공급하는 포인트인가.
     *
     * <p>{@code null} 은 {@link #VIEW01} 로 본다 — 컬럼 도입 전 데이터 호환.
     * 따라서 백필을 하지 않아도 기존 장비의 동작은 변하지 않고,
     * 명시적으로 다른 소스를 찍은 포인트만 제외된다.</p>
     */
    public static boolean isView01Fed(PointDataSource source) {
        return source == null || source == VIEW01;
    }
}
