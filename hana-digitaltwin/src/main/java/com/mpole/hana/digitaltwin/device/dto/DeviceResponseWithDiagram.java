package com.mpole.hana.digitaltwin.device.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

/**
 * DeviceResponse 를 그대로 펼치고(@JsonUnwrapped) 계통도 배선(wirings)만 덧붙인 응답.
 * JSON: 기존 device 필드가 top-level 로 유지되고 wirings 배열이 추가된다.
 */
public record DeviceResponseWithDiagram(
        @JsonUnwrapped DeviceResponse device,
        List<DeviceWiringResponse> wirings
) {
}
