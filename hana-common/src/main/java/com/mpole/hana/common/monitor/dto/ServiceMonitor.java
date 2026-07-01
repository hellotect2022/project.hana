package com.mpole.hana.common.monitor.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * 단일 서비스의 모니터링 결과.
 * 생존 상태와 health 응답 시간, JVM/프로세스 리소스 사용량을 함께 표현한다.
 */
@Builder
public record ServiceMonitor(
        String service,         // 서비스명 (예: hana-event)
        LiveStatus status,      // 생존 상태
        Long responseTimeMs,    // health 응답 시간(ms), 미응답이면 null
        ResourceUsage resource, // 리소스 사용량, 수집 실패 시 null 가능
        String detail,          // 비고/에러 메시지 등 optional
        Long pid,               // 프로세스 PID, 수집 실패 시 null
        OffsetDateTime startedAt // 프로세스 시작 시각, 수집 실패 시 null
) {
}
