package com.mpole.hana.gateway.scheduler;

import com.mpole.hana.gateway.config.properties.HdtPollingProperties;
import com.mpole.hana.gateway.ingestion.dbview.DbViewPublishPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hdt.polling", name = "enabled", havingValue = "true")
public class DbViewPollingScheduler implements SchedulingConfigurer {
    private final ThreadPoolTaskScheduler dbPollingScheduler; // Bean으로 등록된 스케줄러 주입
    private final HdtPollingProperties properties;
    private final DbViewPublishPipeline dbViewPublishPipeline;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        log.info("Hdt Polling 스케줄러를 등록합니다. 주기: {}ms", properties.fixedDelayMs());

        taskRegistrar.setTaskScheduler(dbPollingScheduler);

        taskRegistrar.addFixedDelayTask(
                new FixedDelayTask(
                        () -> {
                            log.info("DB 폴링 시작 지난 사이클에 처리된 데이타 -{}개", dbViewPublishPipeline.getAndResetCount());
                            dbViewPublishPipeline.runOneCycle().block();
                        },
                        Duration.ofMillis(properties.fixedDelayMs()), // 주기
                        Duration.ofMillis(0) // 초기 지연시간(initial delay)
                )
        );
    }
}
