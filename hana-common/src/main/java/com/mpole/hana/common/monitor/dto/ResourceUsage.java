package com.mpole.hana.common.monitor.dto;

import lombok.Builder;

/**
 * JVM/프로세스 레벨 리소스 사용량 지표.
 * 힙, CPU 부하, 스레드 수, 가동 시간 등 단일 JVM 프로세스 관점의 측정값만 담는다.
 */
@Builder
public record ResourceUsage(
        long heapUsedBytes,    // 사용 힙 바이트
        long heapMaxBytes,     // 최대 힙 바이트
        double processCpuLoad, // 0.0~1.0, 측정 불가 시 -1.0
        double systemCpuLoad,  // 0.0~1.0, 측정 불가 시 -1.0
        int threadCount,       // 현재 스레드 수
        long uptimeMs,         // JVM 가동 시간(ms)
        double diskUsage       // 해당 서비스가 뜬 호스트의 디스크 사용률(0.0~1.0, 측정 불가 시 -1.0)
) {
}
