package com.mpole.hana.common.dto;

import com.mpole.hana.common.constant.ConnectionState;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * 인프라(중계기 등) 네트워크 상태 스냅샷 1건 — gateway(vwDigitalTwin_02 폴링) → Kafka 전용 토픽 → event 소비.
 * <p>refInfraCode 는 tbl_device_infra.ref_infra_code 와 매칭되는 외부 식별자(soft key).
 * event 는 등록된 인프라 행만 갱신하며, 미등록 코드는 정합성 리포트 대상.</p>
 */
@Builder
public record InfraStatus(
        String refInfraCode,
        ConnectionState networkState,
        OffsetDateTime updateDatetime
) {
}
