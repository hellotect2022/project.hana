package com.mpole.hana.gateway.scheduler;

import com.mpole.hana.gateway.config.properties.View02Properties;
import com.mpole.hana.gateway.ingestion.dbview.view02.InfraBootstrapService;
import com.mpole.hana.gateway.ingestion.dbview.view02.View02PublishPipeline;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <b>외부 뷰 #02(중계기 네트워크 상태) 폴링 스케줄러.</b>
 *
 * <p>구조는 뷰 #01 과 동일하고 <b>대상 파이프라인·설정·스레드풀만 다르다</b>
 * — 뷰가 늘어도 같은 모양으로 추가하면 된다.</p>
 *
 * <p>전용 스레드풀({@code view02TaskScheduler})을 쓰는 이유: 이 뷰의 조회가 지연돼도
 * 1초 주기인 뷰 #01 값 수집이 함께 멈추면 안 되기 때문이다.</p>
 *
 * <p>뷰 #01 과 달리 {@code matchIfMissing} 을 두지 않는다 — 상태 수집은 뷰 스키마와
 * {@code state-map}(원시 코드 → ConnectionState) 이 확정된 뒤에만 켜야 하므로 <b>명시적 opt-in</b>이 맞다.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hdt.dbview.view02", name = "enabled", havingValue = "true")
public class View02PollingScheduler {

    private final View02Properties properties;
    private final View02PublishPipeline pipeline;
    private final InfraBootstrapService infraBootstrapService;

    /**
     * 기동 시 인프라 마스터 동기화(2026-10-16) — vwDigitalTwin_02 → tbl_device_infra upsert.
     * 실패해도 기동은 계속한다(다음 기동 때 다시 맞춘다).
     */
    @PostConstruct
    public void bootstrapInfraOnStartup() {
        if (!properties.bootstrapInfraOnStart()) return;
        try {
            infraBootstrapService.syncFromView().block(properties.blockTimeout());
        } catch (Exception e) {
            log.warn("[InfraBootstrap] 기동 인프라 동기화 실패(기동은 계속): {}", e.getMessage());
        }
    }

    @Scheduled(
            fixedDelayString = "${hdt.dbview.view02.fixed-delay-ms:10000}",
            initialDelayString = "1000",
            scheduler = "view02TaskScheduler"
    )
    public void poll() {
        try {
            // runOneCycleSafely 가 리액티브 스트림 내부 에러를 흡수하고,
            // 아래 catch 는 타임아웃 등 block() 자체의 예외를 받는다.
            pipeline.runOneCycleSafely().block(properties.blockTimeout());
        } catch (Exception e) {
            log.error("[View02] 사이클 실패(다음 주기 재시도): {}", e.getMessage());
        }
    }
}
