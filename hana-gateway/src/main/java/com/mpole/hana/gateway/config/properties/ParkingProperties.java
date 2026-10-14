package com.mpole.hana.gateway.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * <b>주차 입차·출차 적재</b> 설정 (2026-10-13).
 *
 * <p>주차 시스템 MSSQL(DRPKDBV5)의 프로시저 2개를 주기적으로 불러 우리 PostgreSQL
 * ({@code tbl_parking_in}/{@code tbl_parking_out})에 저장한다. Kafka 는 거치지 않는다.
 * 접속 정보는 {@code storage.parking-mssql.*}(DatabaseConfig) 에 따로 있다.</p>
 *
 * <p>설정 프리픽스: {@code hdt.parking}.</p>
 */
@ConfigurationProperties(prefix = "hdt.parking")
public record ParkingProperties(
        /** 적재 사용 여부. false 면 스케줄러 빈을 만들지 않는다. */
        @DefaultValue("false") boolean enabled,
        /** 주기(ms). 앞 실행이 끝난 뒤부터 센다(fixedDelay) — 느려져도 겹쳐 돌지 않는다. 기본 5분. */
        @DefaultValue("300000") long fixedDelayMs,
        /** 입차 프로시저. 인자는 'yyyyMMdd' 날짜 하나. */
        @DefaultValue("UP_SearchInCarinfo") String inProcedure,
        /** 출차 프로시저. 인자는 'yyyyMMdd' 날짜 하나(출차일 기준). */
        @DefaultValue("UP_SearchoutCarinfo") String outProcedure,
        /**
         * 자정 직후 이 시간 동안은 어제 날짜도 한 번 더 부른다 — 23:5x 출차가 자정 넘어 반영되는 경우를 잡는다.
         * 0 이면 끈다.
         */
        @DefaultValue("30m") Duration previousDayWindow,
        /** 한 회차(프로시저 2개 + 적재) 대기 상한. 넘으면 그 회차는 포기하고 다음 주기에 재시도. */
        @DefaultValue("60s") Duration blockTimeout
) {
}
