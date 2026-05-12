package com.mpole.hana.gateway.scheduler;

import com.mpole.hana.gateway.config.properties.HdtPollingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hdt.polling", name = "enabled", havingValue = "true")
public class DbViewPollingScheduler implements SchedulingConfigurer {

    private final HdtPollingProperties properties;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        log.info("Hdt Polling 스케줄러를 등록합니다. 주기: {}ms", properties.fixedDelayMs());

        // 1. 스케줄러 설정 (보통은 기본값을 써도 되지만 필요시 커스텀 가능)
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("db-poll-");
        scheduler.initialize();

        taskRegistrar.setScheduler(scheduler);
        // 2. 고정 지연(fixedDelay) 작업 등록
        taskRegistrar.addFixedDelayTask(
                () -> {
                    log.info("출력 테스트 ㅋㅋㅋ - 주기: {}ms", properties.fixedDelayMs());
                    // 실제 호출할 메서드: pollingService.pollDbViewCycle();
                },
                Duration.ofMillis(properties.fixedDelayMs())
        );
    }
}
