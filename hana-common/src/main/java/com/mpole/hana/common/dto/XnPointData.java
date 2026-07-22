package com.mpole.hana.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder(toBuilder = true)
public record XnPointData(
        Long deviceId, // 우리쪽 DB 의 deviceId
        Long pointId,  // 우리쪽 DB 의 pointId
        String pointkey, // 우리쪽 DB 의 pointKey
        String deviceCode,
        String pointCode,
        //@JsonProperty("pointkey") String pointKey,
        String alarmYn,
        String objectType,
        String pointName,
        String valueRaw,
        String unit,
        String tagName,
        OffsetDateTime updateDatetime
) {
}
