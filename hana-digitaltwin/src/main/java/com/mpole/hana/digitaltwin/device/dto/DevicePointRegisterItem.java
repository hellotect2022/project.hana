package com.mpole.hana.digitaltwin.device.dto;

/**
 * 장비 등록 시 함께 저장할 관제점 항목.
 *
 * @param dataSource 값의 원천 수집 경로 코드(예: {@code VIEW01}, {@code AMQP}, {@code DERIVED}).
 *                   미지정이면 null 로 저장되고 하위호환으로 {@code VIEW01} 로 간주된다.
 *                   <p>RabbitMQ·파생값으로 채우는 포인트는 <b>반드시 지정</b>해야 한다 —
 *                   VIEW01 로 간주되면 gateway 기동 시 그 장비의 값이 null 로 덮인다.</p>
 */
public record DevicePointRegisterItem(
        String schemaTagName,
        String tagName,
        String pointKey,
        String description,
        String pointDisplayName,
        String pointType,
        String unit,
        Boolean isDisplay,
        String dataSource
) {
}
