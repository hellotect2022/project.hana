package com.mpole.hana.gateway.scheduler;

import com.mpole.hana.gateway.config.AsyncExecutorConfig;
import com.mpole.hana.gateway.config.properties.ParkingProperties;
import com.mpole.hana.gateway.ingestion.parking.ParkingSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <b>주차 입차·출차 적재 스케줄러</b> (2026-10-13).
 *
 * <ul>
 *   <li>전용 스레드({@code parkingTaskScheduler}, 1개) — 외부 주차 DB 가 느려져도 뷰 #01 폴링이 밀리지 않는다.</li>
 *   <li>{@code fixedDelay} 5분 — 앞 회차가 끝난 뒤부터 센다. 느려져도 회차가 겹쳐 돌지 않는다.</li>
 *   <li>기동 30초 뒤 첫 회차 — 기동 직후 다른 초기화와 몰리지 않게.</li>
 *   <li>한 회차가 {@code block-timeout} 을 넘으면 포기하고 다음 주기에 재시도(스레드를 붙잡지 않는다).</li>
 * </ul>
 *
 * <p>{@code hdt.parking.enabled=true} 일 때만 빈이 생긴다(명시적 opt-in).</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hdt.parking", name = "enabled", havingValue = "true")
public class ParkingSyncScheduler {

    private final ParkingProperties properties;
    private final ParkingSyncService syncService;

    @Scheduled(
            fixedDelayString = "${hdt.parking.fixed-delay-ms:300000}",
            //initialDelayString = "30000",
            scheduler = AsyncExecutorConfig.PARKING_TASK_SCHEDULER
    )
    public void sync() {
        try {
            syncService.runOneCycleSafely().block(properties.blockTimeout());
        } catch (Exception e) {
            log.error("[Parking] 회차 실패(다음 주기 재시도): {}", e.getMessage());
        }
    }
}
