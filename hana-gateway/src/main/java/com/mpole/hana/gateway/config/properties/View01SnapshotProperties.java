package com.mpole.hana.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * <b>뷰 #01 스냅샷 파이프라인</b> 설정 — 기존 {@code View01Properties}(델타 파이프라인)와 별개.
 *
 * <p>기존 경로를 건드리지 않고 새 경로를 추가한 것이라, 둘 중 <b>하나만 켜야 한다</b>:</p>
 * <pre>
 *   기존 경로:  hdt.dbview.view01.enabled = true   (기본 ON — matchIfMissing)
 *   신규 경로:  hdt.dbview.view01.snapshot.enabled = true
 * </pre>
 * <p>둘 다 켜면 같은 뷰를 두 번 폴링하고 Kafka 에 이중 발행된다.
 * 신규 경로로 전환할 때는 반드시 {@code hdt.dbview.view01.enabled: false} 로 내려야 한다.</p>
 */
@ConfigurationProperties(prefix = "hdt.dbview.view01.snapshot")
public record View01SnapshotProperties(
        /** 스냅샷 파이프라인 사용 여부. 기본 false — 기존 경로가 계속 동작한다. */
        @DefaultValue("false") boolean enabled,
        /** 폴링 주기(ms). */
        @DefaultValue("1000") long fixedDelayMs,
        /** 골격(tbl_device_point) 리로드 주기(ms). 값은 이월되므로 잦아도 손실은 없다. */
        @DefaultValue("30000") long metaRefreshMs,
        /** 사이클 blocking 상한 — 초과 시 예외로 끊어 스케줄러 스레드 점유를 막는다. */
        @DefaultValue("60s") Duration blockTimeout,
        /**
         * 기동 직후 1회 전 장비를 발행할지 여부.
         * <p>true 면 하류(current_value)가 재기동 직후 전체 골격으로 한 번 동기화된다.</p>
         */
        @DefaultValue("true") boolean publishAllOnStart
) {
}
