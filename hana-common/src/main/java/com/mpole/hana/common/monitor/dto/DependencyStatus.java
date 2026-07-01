package com.mpole.hana.common.monitor.dto;

import lombok.Builder;

/**
 * actuator 를 노출하지 않는 외부 인프라 의존처(DB/Kafka 등)의 TCP 도달성 상태.
 */
@Builder
public record DependencyStatus(
        String name,        // 표시명 (예: DB, Kafka)
        String target,      // host:port
        LiveStatus status   // UP/DOWN/UNKNOWN
) {
}
