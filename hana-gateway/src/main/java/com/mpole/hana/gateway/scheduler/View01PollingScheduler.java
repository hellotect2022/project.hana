package com.mpole.hana.gateway.scheduler;

import com.mpole.hana.gateway.config.properties.View01Properties;
import com.mpole.hana.gateway.ingestion.dbview.view01.View01PublishPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <b>외부 뷰 #01(관제점 값) 폴링 스케줄러.</b>
 *
 * <p>Spring 스케줄링에 {@code scheduler} 속성으로 <b>전용 스레드풀</b>({@code view01TaskScheduler})을 지정한다.
 * 뷰 #02 는 자기 풀을 따로 쓰므로 한쪽 조회 지연이 다른 쪽을 멈추지 않는다.</p>
 *
 * <p><b>WebFlux 와의 관계</b> — 이 메서드는 Netty 이벤트 루프가 아니라 위 전용 풀의 스레드에서 실행되므로
 * {@code block()} 이 안전하다(이벤트 루프/‌{@code Schedulers.parallel()} 스레드에서만 금지).
 * 대신 반드시 <b>타임아웃을 건다</b> — 소스 DB 가 응답하지 않을 때 폴링 스레드가 영구 점유되는 것을 막는다.</p>
 *
 * <p>{@code fixedDelay} 라 이전 사이클이 끝난 뒤 주기가 시작된다(사이클 겹침 없음).
 * {@code hdt.dbview.view01.enabled} 가 없으면 켠다 — 값 수집은 이 서버의 핵심 기능이라 기본 ON.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hdt.dbview.view01", name = "enabled", havingValue = "true", matchIfMissing = true)
public class View01PollingScheduler {

    private final View01Properties properties;
    private final View01PublishPipeline pipeline;

    @Scheduled(
            fixedDelayString = "${hdt.dbview.view01.fixed-delay-ms:1000}",
            initialDelayString = "0",
            scheduler = "view01TaskScheduler"
    )
    public void poll() {
        log.info("DB 폴링 시작 지난 사이클에 처리된 데이타 -{}개", pipeline.getAndResetCount());
        try {
            pipeline.runOneCycleSafely().block();
        } catch (Exception e) {
            // 한 사이클 실패로 스케줄이 멈추면 안 된다 — 로깅 후 다음 주기에 재시도.
            log.error("[View01] 사이클 실패(다음 주기 재시도): {}", e.getMessage(), e);
        }
    }
}
