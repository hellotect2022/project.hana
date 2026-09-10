package com.mpole.hana.digitaltwin.device.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 포인트 외부코드 매핑 수정 요청.
 *
 * @param dataSource 값의 원천 수집 경로 코드(예: {@code VIEW01}, {@code AMQP}, {@code DERIVED}).
 *                   <p><b>null 이면 기존 값을 유지한다</b>(비우지 않는다) — 이 필드를 모르는 기존 클라이언트가
 *                   매핑을 저장할 때 다른 경로로 지정해 둔 소스가 지워지는 사고를 막기 위함이다.
 *                   지워지면 그 포인트가 {@code VIEW01} 로 간주되어 gateway 발행 대상이 되고,
 *                   RabbitMQ/파생값이 null 로 덮인다.</p>
 *                   <p>되돌리려면 {@code "VIEW01"} 을 명시적으로 보낸다.</p>
 */
public record DevicePointRefMappingRequest(
        @NotNull Long pointId,
        String refDeviceCode,
        String refPointCode,
        String dataSource
) {

    /** 기존 3-인자 호출 호환(소스 미변경). */
    public DevicePointRefMappingRequest(Long pointId, String refDeviceCode, String refPointCode) {
        this(pointId, refDeviceCode, refPointCode, null);
    }
}
