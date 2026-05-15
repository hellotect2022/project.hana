package com.mpole.hana.gateway.config;

import com.mpole.hana.gateway.config.properties.HdtPollingProperties;
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
    public static final String DB_POLLING_SCHEDULER = "dbPollingScheduler";

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

    @Bean(name = DB_POLLING_SCHEDULER)
    ThreadPoolTaskScheduler dbPollingScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("db-poll-");
        scheduler.setPoolSize(1);
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
