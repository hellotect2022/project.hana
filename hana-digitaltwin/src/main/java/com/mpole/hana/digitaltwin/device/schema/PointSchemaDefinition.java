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
        String dataSource
) {
    @JsonCreator
    public PointSchemaDefinition {
        // 이 컴팩트 생성자 형식을 사용하면 Jackson이 필드 이름과 매칭하여 역직렬화합니다.
        // dataSource 가 없는 기존 JSONB 데이터는 null 로 들어옵니다(= 기존 값 유지 / VIEW01 취급).
    }
}
