package com.mpole.hana.gateway.config;

import com.mpole.hana.gateway.config.properties.ModbusIngestionProperties;
import com.mpole.hana.gateway.config.properties.TcpIngestionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

// MODBUS /TCP 같은 블로킹 소켓 워커를 전용 스레드풀 Bean 으로 등록하는 설정 클래스
// Webflux 의 이벤트 루프와 분리해서 사용함
@Configuration
public class AsyncExecutorConfig {
    public static final String MODBUS_TASK_EXECUTOR = "modbusTaskExecutor";
    public static final String TCP_TASK_EXECUTOR = "tcpTaskExecutor";
    /** 외부 뷰 폴링 전용 스케줄러 — 뷰마다 하나씩(@Scheduled(scheduler=...) 로 지정). */
    public static final String VIEW01_TASK_SCHEDULER = "view01TaskScheduler";
    public static final String VIEW02_TASK_SCHEDULER = "view02TaskScheduler";

    @Bean(name = MODBUS_TASK_EXECUTOR)
    ThreadPoolTaskExecutor modbusTaskExecutor(ModbusIngestionProperties props) {
        var pool = props.pool();
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setThreadNamePrefix("modbus-");
        ex.setCorePoolSize(pool.coreSize());
        ex.setMaxPoolSize(pool.maxSize());
        ex.setQueueCapacity(pool.queueCapacity());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }

    @Bean(name = TCP_TASK_EXECUTOR)
    ThreadPoolTaskExecutor tcpTaskExecutor(TcpIngestionProperties props) {
        var pool = props.pool();
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setThreadNamePrefix("tcp-");
        ex.setCorePoolSize(pool.coreSize());
        ex.setMaxPoolSize(pool.maxSize());
        ex.setQueueCapacity(pool.queueCapacity());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }

    /**
     * 뷰 #01(관제점 값) 폴링 전용 스레드.
     * <p>뷰별로 스케줄러를 분리하는 이유: 한쪽 뷰의 조회가 지연돼도 다른 뷰 폴링이 멈추지 않게 하기 위함.
     * poolSize=1 이라 같은 뷰의 사이클끼리는 자연히 직렬화된다.</p>
     */
    @Bean(name = VIEW01_TASK_SCHEDULER)
    ThreadPoolTaskScheduler view01TaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("view01-poll-");
        scheduler.setPoolSize(1);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }

    /** 뷰 #02(중계기 네트워크 상태) 폴링 전용 스레드. 뷰 #01 과 격리. */
    @Bean(name = VIEW02_TASK_SCHEDULER)
    ThreadPoolTaskScheduler view02TaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("view02-poll-");
        scheduler.setPoolSize(1);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }

    /** Runnable 제출용으로는 Executor 타입만 노출해도 됨 */
    @Bean
    Executor modbusExecutor(ThreadPoolTaskExecutor modbusTaskExecutor) {
        return modbusTaskExecutor.getThreadPoolExecutor();
    }

    @Bean
    Executor tcpExecutor(ThreadPoolTaskExecutor tcpTaskExecutor) {
        return tcpTaskExecutor.getThreadPoolExecutor();
    }




    // 주입 예
//    public SocketIngestionLifecycle(
//            @Qualifier(AsyncExecutorsConfig.TCP_TASK_EXECUTOR) Executor tcpExecutor,
//        ...
//    ) { }
}
