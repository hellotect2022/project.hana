package com.mpole.hana.common.monitor.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 여러 서비스 모니터링 집계 스냅샷.
 * 특정 시각에 폴링한 모든 대상 서비스의 모니터링 결과를 한 번에 담는다.
 */
@Builder
public record MonitorSnapshot(
        OffsetDateTime timestamp,             // 스냅샷 시각
        List<ServiceMonitor> services,        // 서비스별 모니터링 결과 목록
        List<DependencyStatus> dependencies   // 인프라 의존처(DB/Kafka 등) TCP 도달성 목록
) {
}
