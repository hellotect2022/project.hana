package com.mpole.hana.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * <b>외부 뷰 #01 (vwDigitalTwin_01) — 관제점 값 수집</b> 설정.
 *
 * <p>SI 측 뷰에서 장비 관제점의 현재값(value/alarm_yn/update_datetime)을 주기 폴링해,
 * 메모리 골격(tbl_device_point)에 값을 채우고 <b>값이 바뀐 장비만 전체 포인트로</b> Kafka 에 발행한다.
 * 뷰 이름을 설정으로 분리해 외부 스키마가 바뀌어도 코드 수정 없이 대응한다.</p>
 *
 * <p>설정 프리픽스: {@code hdt.dbview.view01} (뷰 #02 와 대칭 구조).</p>
 */
@ConfigurationProperties(prefix = "hdt.dbview.view01")
public record View01Properties(
        /** 폴링 사용 여부. 뷰 #02 와 같이 명시적 opt-in. */
        @DefaultValue("false") boolean enabled,
        /** 폴링 주기(ms). 값 데이터라 고빈도. */
        @DefaultValue("1000") long fixedDelayMs,
        /** 조회 대상 뷰(필요 시 스키마 포함: dbo.vwDigitalTwin_01). */
        @DefaultValue("vwDigitalTwin_01") String table,
        /** 골격(tbl_device_point) 리로드 주기(ms). 값은 pointId 기준으로 이월되므로 잦아도 손실은 없다. */
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
