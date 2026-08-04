package com.mpole.hana.digitaltwin.device.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 장비 property 타입 메타 — 카테고리 property_type 선택 / 장비 등록 시 property_info 양식 안내용.
 *
 * <p>{@code template} 은 해당 타입 property 클래스(CctvProperty 등)를 직렬화해 파생한 스켈레톤이라
 * 클래스가 단일 진실원천이 된다(하드코딩 JSON 문자열 없음). 예) CCTV →
 * {@code { "type":"CCTV", "main_url":null, "sub_url":null, "user_id":null, "password":null }}.</p>
 */
public record DevicePropertyTypeResponse(
        String type,        // DevicePropertyType name (CCTV/CHILLER/GATEWAY/GENERIC)
        JsonNode template   // property_info 스켈레톤(클래스에서 파생)
) {
}
