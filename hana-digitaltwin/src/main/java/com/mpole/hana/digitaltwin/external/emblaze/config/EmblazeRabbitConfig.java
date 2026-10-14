package com.mpole.hana.digitaltwin.external.emblaze.config;

import com.mpole.hana.digitaltwin.external.emblaze.amqp.MonitoringQueue;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * emblaze monitoring/control RabbitMQ 연결·컨테이너팩토리·큐 선언.
 * <p>exchange/binding 없이 durable 큐를 직접 선언하고 {@code @RabbitListener} push 수신한다(원본 구조).
 * {@code hdt.integration.emblaze.enabled=true} 일 때만 기동(기본 OFF). 자격증명은 {@link EmblazeProperties}(env/yml 주입).</p>
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "hdt.integration.emblaze.enabled", havingValue = "true", matchIfMissing = false)
public class EmblazeRabbitConfig {

    @Bean
    @Primary
    public ConnectionFactory monitoringConnectionFactory(EmblazeProperties props) {
        EmblazeProperties.Endpoint m = props.getRabbit().getMonitoring();
        CachingConnectionFactory f = new CachingConnectionFactory(props.getRabbit().getHost(), m.getPort());
        f.setUsername(m.getUsername());
        f.setPassword(m.getPassword());
        f.setVirtualHost(m.getVirtualHost());
        return f;
    }

    @Bean
    public ConnectionFactory controlConnectionFactory(EmblazeProperties props) {
        EmblazeProperties.Endpoint c = props.getRabbit().getControl();
        CachingConnectionFactory f = new CachingConnectionFactory(props.getRabbit().getHost(), c.getPort());
        f.setUsername(c.getUsername());
        f.setPassword(c.getPassword());
        f.setVirtualHost(c.getVirtualHost());
        return f;
    }

    @Bean(name = "monitoringRabbitTemplate")
    public RabbitTemplate monitoringRabbitTemplate(
            @Qualifier("monitoringConnectionFactory") ConnectionFactory monitoringConnectionFactory) {
        return new RabbitTemplate(monitoringConnectionFactory);
    }

    @Bean(name = "controlRabbitTemplate")
    public RabbitTemplate controlRabbitTemplate(
            @Qualifier("controlConnectionFactory") ConnectionFactory controlConnectionFactory) {
        return new RabbitTemplate(controlConnectionFactory);
    }

    // 메시지 비동기 수신 리스너 컨테이너 팩토리
    @Bean
    public SimpleRabbitListenerContainerFactory monitoringContainerFactory(
            @Qualifier("monitoringConnectionFactory") ConnectionFactory monitoringConnectionFactory,
            EmblazeProperties props) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(monitoringConnectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO); // 필요시 MANUAL
        factory.setDefaultRequeueRejected(false);

        // 재연결 백오프: 브로커가 계속 안 뜨면 지수 백오프로 재시도하다가
        // maxElapsedMs 경과 시 STOP → 컨슈머가 멈춰(무한 재시도로 리소스 먹지 않도록).
        EmblazeProperties.Recovery r = props.getRabbit().getRecovery();
        ExponentialBackOff backOff = new ExponentialBackOff(r.getInitialIntervalMs(), r.getMultiplier());
        backOff.setMaxInterval(r.getMaxIntervalMs());
        backOff.setMaxElapsedTime(r.getMaxElapsedMs());
        factory.setRecoveryBackOff(backOff);
        return factory;
    }

    @Bean
    public Declarables monitoringQueues() {
        List<Declarable> queues = Arrays.stream(MonitoringQueue.values())
                .map(q -> new Queue(q.getQueueName(), true))
                .collect(Collectors.toList());
        return new Declarables(queues);
    }
}
