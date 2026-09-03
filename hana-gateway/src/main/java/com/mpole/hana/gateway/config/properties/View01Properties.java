package com.mpole.hana.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * <b>외부 뷰 #01 (vwDigitalTwin_01) — 포인트 값 수집</b> 설정.
 *
 * <p>SI 측 뷰에서 장비 관제점의 현재값(value/alarm_yn/update_datetime)을 주기 폴링해
 * 변경분만 Kafka 로 발행하는 파이프라인의 설정이다. 뷰 이름을 설정으로 분리해
 * 외부 스키마가 바뀌어도 코드 수정 없이 대응한다.</p>
 *
 * <p>설정 프리픽스: {@code hdt.dbview.view01} (뷰 #02 와 대칭 구조).</p>
 */
@ConfigurationProperties(prefix = "hdt.dbview.view01")
public record View01Properties(
        /** 폴링 사용 여부. */
        @DefaultValue("true") boolean enabled,
        /** 폴링 주기(ms). 값 데이터라 고빈도. */
        @DefaultValue("1000") long fixedDelayMs,
        /** 조회 대상 뷰(필요 시 스키마 포함: dbo.vwDigitalTwin_01). */
        @DefaultValue("vwDigitalTwin_01") String table,
        /** 사이클 blocking 상한 — 초과 시 예외로 끊어 스케줄러 스레드 점유를 막는다. */
        @DefaultValue("5s") Duration blockTimeout
) {
}
