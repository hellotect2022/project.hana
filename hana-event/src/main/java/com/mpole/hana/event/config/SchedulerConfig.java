package com.mpole.hana.event.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 주기 작업 전용 스케줄러 — gateway 의 {@code AsyncExecutorConfig}(viewNN TaskScheduler)와 같은 방식.
 *
 * <p>{@code @Scheduled(scheduler = "eventTaskScheduler")} 로 지정한 작업(규칙 캐시 리로드, ack 스윕)이
 * 여기서 돈다. poolSize=1 이라 두 작업은 자연히 직렬화되고(둘 다 30초 주기·가벼움),
 * 평가 파이프라인 스레드(event-eval / boundedElastic)와 격리된다.</p>
 */
@Configuration
public class SchedulerConfig {
    public static final String EVENT_TASK_SCHEDULER = "eventTaskScheduler";

    @Bean(name = EVENT_TASK_SCHEDULER)
    ThreadPoolTaskScheduler eventTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("event-refresh-");
        scheduler.setPoolSize(1);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }
}
