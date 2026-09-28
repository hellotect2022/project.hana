package com.mpole.hana.digitaltwin.external.hqs.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * HQS 아웃바운드 전송 전용 스레드풀.
 *
 * <h3>왜 필요한가</h3>
 * <p>HQS 호출은 동기 블로킹이다({@code RestClient} + {@code SimpleClientHttpRequestFactory}).
 * 이걸 RabbitMQ 리스너 스레드에서 직접 하면 <b>응답을 기다리는 동안 그 큐의 수신이 통째로 멈춘다</b>
 * — enocean 컨슈머는 1개뿐이라 다음 버튼 메시지는 손도 못 댄다.
 * 타임아웃이 connect 3s + read 3s 이고 인증 실패 시 재발급 후 1회 재시도가 붙으므로
 * 최악 18초다. 그동안 버튼의 원본값 기록까지 같이 밀린다(HQS 와 무관한 일인데도).</p>
 *
 * <p>전송을 이 풀로 넘기면 리스너는 밀리초 단위로 반환한다.</p>
 *
 * <h3>큐를 반드시 유한하게 둔다</h3>
 * <p>{@code @EnableAsync} 만 켜고 빈을 지정하지 않으면 Spring Boot 기본
 * {@code applicationTaskExecutor}(queue-capacity = {@code Integer.MAX_VALUE})가 쓰인다.
 * 그러면 HQS 가 죽어 있는 동안 <b>힙에 무한정 쌓여 OOM</b> 이다 — 지금은 브로커가 막아주는 적체를
 * 우리 메모리로 끌어오는 꼴이 된다. 그래서 <b>{@code @Async} 는 반드시 이름을 지정해서</b>
 * 이 빈을 쓴다.</p>
 *
 * <p>큐가 차면 {@link ThreadPoolExecutor.AbortPolicy} 로 거부한다 — 호출부가
 * {@code RejectedExecutionException}(Spring 은 {@code TaskRejectedException} 으로 감싸며,
 * 그것도 같은 타입이다)을 받아 사유를 남기고 <b>그 누름을 버린다</b>.
 * {@code CallerRunsPolicy} 는 쓰지 않는다 — 리스너 스레드가 대신 실행하게 되어
 * 애써 떼어낸 블로킹이 그대로 돌아온다.</p>
 *
 * <h3>큐를 작게, 스레드를 여럿으로 두는 이유</h3>
 * <p>{@link ThreadPoolExecutor} 는 <b>큐가 꽉 찬 다음에야 스레드를 늘린다.</b> 반직관적이라
 * 자주 틀리는 부분이다 — {@code core=1, max=32, queue=500} 으로 두면 500개가 쌓이기 전까지
 * 두 번째 스레드가 생기지 않아 <b>사실상 1개짜리 풀</b>이 된다.
 * 동시에 보내고 싶으면 {@code max} 가 아니라 <b>{@code core} 를 올려야 한다.</b></p>
 *
 * <pre>
 *   유입 → core 개까지 즉시 스레드 배정(대기 없음)
 *        → 다 바쁘면 큐(짧게)
 *        → 그것도 차면 거부
 * </pre>
 *
 * <p>필요한 동시성은 {@code 유입률 × 응답시간} 이다. 응답 6초에 초당 10건이면 60이지만,
 * 그 구간은 동시성을 늘려서 풀 문제가 아니다 — HQS 가 느린 것이고 서킷 브레이커로 끊어야 한다.
 * 수백 동시 연결은 우리가 HQS 를 두들기는 꼴이 된다.</p>
 *
 * <p>순서는 보장하지 않는다. 버튼 누름은 각각 독립 사건이고 타임스탬프를 들고 가므로
 * 도착 순서가 의미를 갖지 않는다.</p>
 *
 * <p>{@code HqsTokenProvider.login()} 은 {@code synchronized} + stale 토큰 이중검사라
 * 스레드 여럿이 동시에 만료를 만나도 <b>로그인은 한 번만</b> 나간다.</p>
 *
 * <p><b>{@code @EnableAsync} 는 애플리케이션 전역 스위치다.</b> 현재 digitaltwin 에
 * {@code @Async} 가 붙은 다른 메서드는 없어서 켜도 기존 동작이 바뀌지 않는다.
 * 앞으로 {@code @Async} 를 쓸 때는 <b>반드시 executor 이름을 지정</b>할 것.</p>
 */
@Configuration
@EnableAsync
public class HqsAsyncConfig {

    /** HQS 아웃바운드 전송 executor 빈 이름. {@code @Async} 에 이 값을 반드시 지정한다. */
    public static final String HQS_OUTBOUND_EXECUTOR = "hqsOutboundExecutor";

    /**
     * 동시에 보낼 수 있는 전송 수. 들어오는 대로 여기까지는 <b>대기 없이</b> 나간다.
     * <p>{@code max} 가 아니라 이 값이 동시성을 결정한다(위 주석 참고).</p>
     */
    @Value("${hdt.integration.hqs.outbound.pool-size:16}")
    private int poolSize;

    /**
     * 대기 큐 길이. 스레드가 다 찼을 때 <b>짧은 버스트만</b> 흡수한다.
     * <p>길게 두면 안 된다 — 큐가 길수록 스레드가 안 늘어나고(위 주석), 지연된 누름을
     * 한참 뒤에 보내봐야 알림으로서 의미가 없다.</p>
     */
    @Value("${hdt.integration.hqs.outbound.queue-capacity:50}")
    private int queueCapacity;

    @Bean(name = HQS_OUTBOUND_EXECUTOR)
    public ThreadPoolTaskExecutor hqsOutboundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("hqs-out-");
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 한가할 때는 스레드를 반납한다. 버튼은 드물게 몰리는 부하라, 한 번 몰렸다고
        // 16개가 영원히 파킹돼 있을 이유가 없다(core==max 면 기본적으로 안 죽는다).
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        // 종료 시 진행 중인 전송을 기다린다 — shutdownNow 로 끊으면 이미 HQS 에 도달한 요청의
        // 결과를 우리가 못 보고, 로그에도 남지 않는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        // initialize() 를 직접 부르지 않는다 — ThreadPoolTaskExecutor 는 InitializingBean 이라
        // 스프링이 afterPropertiesSet() 에서 부른다. 여기서 또 부르면 풀이 두 번 만들어지고
        // 먼저 만든 쪽이 버려진다(gateway 의 AsyncExecutorConfig 는 그렇게 돼 있다).
        return executor;
    }
}
