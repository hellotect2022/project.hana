package com.mpole.hana.digitaltwin.device.schema;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 카테고리 관제점 스키마 정의 1건.
 *
 * @param dataSource 이 태그 값의 원천 수집 경로 코드(예: {@code VIEW01}, {@code AMQP}, {@code DERIVED}).
 *                   <p>소스는 "이 태그의 값이 어느 파이프라인으로 들어오는가"라 <b>장비 종류(카테고리) 수준</b>에서
 *                   결정된다 — 부스 카테고리의 {@code occ} 는 모든 부스에서 AMQP 다. 그래서 type·unit 과 함께
 *                   스키마 필드로 둔다(ref_device_code 처럼 인스턴스마다 다른 값이 아니다).</p>
 *                   <p><b>null 이면 기존 포인트의 값을 유지한다</b>(비우지 않는다) — 이 필드를 채우지 않은
 *                   기존 스키마를 저장했다고 해서 매핑 화면에서 개별 지정한 소스가 지워지면 안 되기 때문이다.
 *                   되돌리려면 {@code "VIEW01"} 을 명시한다.</p>
 *                   <p>신규 생성 포인트에는 그대로 들어간다(null 이면 null = VIEW01 취급).</p>
 */
public record PointSchemaDefinition(
        String tagName,
        String type,
        String unit,
        String tagDesc,
        String tagDisplayName,
        boolean isDisplay,
        String dataSource,
        Integer sortOrder,
        Boolean hud,
        /*
         * 2026-10-12 저장 설정 3종 — 관리자 화면(카테고리 관리 → 포인트 스키마 "고급")에서 편집.
         *   accumulate  누적 포인트(누적 전력량 등). true 면 event 서버가 5분 스냅샷(tbl_energy_snapshot)을 찍고 금일·금월 계산 대상
         *   scale       배율. event 서버가 Kafka 수신 직후 원본 × scale 로 바꿔 저장·평가·전송한다(원본은 남지 않음). null/1 = 그대로
         *   historyMode 값 이력 저장 방식. CHANGE(기본, 변화 시) / NONE(저장 안 함). 포인트 단위 이력(후속 단계)에서 사용
         * 없는 기존 JSONB 는 null 로 들어오며 각각 false · 1 · CHANGE 로 본다.
         */
        Boolean accumulate,
        Double scale,
        String historyMode
) {
    @JsonCreator
    public PointSchemaDefinition {
        // 이 컴팩트 생성자 형식을 사용하면 Jackson이 필드 이름과 매칭하여 역직렬화합니다.
        // dataSource / sortOrder / hud 가 없는 기존 JSONB 데이터는 null 로 들어옵니다.
    }

    /**
     * 표시 순서. 화면에서 행을 끌어 옮긴 순서대로 0부터 부여된다.
     *
     * <p>{@code null}(이 필드 도입 전 데이터)이면 <b>배열에 담긴 순서</b>가 곧 의도한 순서이므로,
     * 정렬할 때 원래 인덱스를 그대로 쓰면 된다 — 백필 없이 배포할 수 있다.</p>
     */
    public int sortOrderOr(int fallback) {
        return sortOrder == null ? fallback : sortOrder;
    }

    /**
     * HUD 표출 여부. {@code null}(미지정)은 <b>false</b> 로 본다 —
     * 기존 카테고리가 전부 갑자기 HUD 에 뜨면 안 되기 때문이다.
     */
    public boolean hudEnabled() {
        return Boolean.TRUE.equals(hud);
    }
}
