package com.mpole.hana.gateway.scheduler;

import com.mpole.hana.gateway.config.properties.View01SnapshotProperties;
import com.mpole.hana.gateway.ingestion.dbview.view01.snapshot.DeviceSnapshotStore;
import com.mpole.hana.gateway.ingestion.dbview.view01.snapshot.View01SnapshotPublishPipeline;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <b>뷰 #01 스냅샷 파이프라인 스케줄러.</b>
 *
 * <p>두 가지 주기 작업을 돌린다:</p>
 * <ol>
 *   <li><b>값 폴링</b> — 뷰를 읽어 골격에 값을 적용하고 변경 장비를 발행</li>
 *   <li><b>골격 리로드</b> — tbl_device_point 를 다시 읽어 포인트 추가/삭제를 반영(값은 이월됨)</li>
 * </ol>
 *
 * <p>기동 시 골격을 먼저 <b>블로킹으로</b> 적재한다 — 골격이 비어 있으면 뷰 값을 어디에도 쓸 수 없어
 * 첫 사이클이 통째로 버려지기 때문이다.</p>
 *
 * <p>⚠ 기존 델타 경로({@code View01PollingScheduler})와 <b>동시에 켜면 안 된다</b>.
 * 같은 뷰를 두 번 폴링하고 Kafka 에 이중 발행된다. 전환 시 {@code hdt.dbview.view01.enabled: false}.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hdt.dbview.view01.snapshot", name = "enabled", havingValue = "true")
public class View01SnapshotPollingScheduler {

    private final View01SnapshotProperties properties;
    private final DeviceSnapshotStore store;
    private final View01SnapshotPublishPipeline pipeline;

    /**
     * 기동 시 골격 초기 적재(블로킹). 실패하면 기동을 실패시키는 편이 낫다 —
     * 골격 없이 뜨면 아무 값도 매핑되지 않은 채 조용히 도는 상태가 된다.
     */
    @PostConstruct
    public void loadSkeletonOnStartup() {
        store.refresh().block();
        log.info("[Snapshot] 초기 골격 적재 완료 — 장비 {}대, 신호키 {}개", store.deviceCount(), store.signalKeyCount());

        if (properties.publishAllOnStart()) {
            // 재기동 직후 하류(current_value)를 전체 골격으로 한 번 동기화한다.
            store.markAllDirty();
            log.info("[Snapshot] 기동 전량 발행 예약 — 다음 사이클에 {}대 발행", store.deviceCount());
        }
    }

    @Scheduled(
            fixedDelayString = "${hdt.dbview.view01.snapshot.fixed-delay-ms:1000}",
            initialDelayString = "0",
            scheduler = "view01TaskScheduler"
    )
    public void poll() {
        try {
            pipeline.runOneCycleSafely().block(properties.blockTimeout());
        } catch (Exception e) {
            log.error("[Snapshot] 폴링 실패(다음 주기 재시도): {}", e.getMessage());
        }
    }

    /**
     * 골격 리로드 — 포인트 매핑이 관리자 화면에서 바뀌어도 재기동 없이 반영된다.
     * 값은 pointId 기준으로 이월되므로 리로드로 인한 값 유실은 없다.
     */
    @Scheduled(
            fixedDelayString = "${hdt.dbview.view01.snapshot.meta-refresh-ms:30000}",
            initialDelayString = "${hdt.dbview.view01.snapshot.meta-refresh-ms:30000}",
            scheduler = "view01TaskScheduler"
    )
    public void refreshSkeleton() {
        try {
            store.refresh().block(properties.blockTimeout());
        } catch (Exception e) {
            log.error("[Snapshot] 골격 리로드 실패(이전 골격 유지): {}", e.getMessage());
        }
    }
}
