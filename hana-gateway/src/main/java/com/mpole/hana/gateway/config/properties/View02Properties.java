package com.mpole.hana.gateway.config.properties;

import com.mpole.hana.common.constant.ConnectionState;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * <b>외부 뷰 #02 (vwDigitalTwin_02) — 중계기 네트워크 상태 수집</b> 설정.
 *
 * <p>뷰 #01 이 "관제점 값"을 가져온다면, 뷰 #02 는 "중계기(인프라)가 살아있는지"를 가져온다.
 * 행 수가 적고 저빈도라 변경감지(delta) 없이 매 사이클 전량을 스냅샷으로 발행한다.</p>
 *
 * <p><b>식별/상태 컬럼명과 상태코드 매핑을 설정으로 분리</b>한 이유: 외부(SI) 스키마와 코드 체계는
 * 우리가 통제할 수 없고 변경될 수 있다. 코드에 박으면 그때마다 배포해야 한다.</p>
 *
 * <p>설정 프리픽스: {@code hdt.dbview.view02} (뷰 #01 과 대칭 구조).</p>
 */
@ConfigurationProperties(prefix = "hdt.dbview.view02")
public record View02Properties(
        /** 폴링 사용 여부. 뷰 스키마·상태코드 확정 전에는 false 로 둔다. */
        @DefaultValue("false") boolean enabled,
        /** 폴링 주기(ms). 상태 데이터라 값보다 저빈도. */
        @DefaultValue("10000") long fixedDelayMs,
        /** 조회 대상 뷰(필요 시 스키마 포함: dbo.vwDigitalTwin_02). */
        @DefaultValue("vwDigitalTwin_02") String table,
        /** 중계기 식별 컬럼 — 이 값이 tbl_device_infra.ref_infra_code 와 매칭된다. */
        @DefaultValue("device_code") String codeColumn,
        /** 원시 상태 컬럼(1/2/3 코드). */
        @DefaultValue("status") String stateColumn,
        /**
         * 원시 상태코드 → {@link ConnectionState} 매핑.
         * <p>여기 정의되지 않은 코드는 {@code UNKNOWN} 으로 처리한다 — 미지의 코드가
         * 조용히 '정상'으로 둔갑해 장애를 못 보는 상황을 막기 위함이다.</p>
         */
        Map<String, ConnectionState> stateMap,
        /** 사이클 blocking 상한 — 초과 시 예외로 끊어 스케줄러 스레드 점유를 막는다. */
        @DefaultValue("30s") Duration blockTimeout,
        /**
         * gateway 기동 시 이 뷰의 장치 목록을 tbl_device_infra 에 upsert 할지 (2026-10-16).
         * 실행 위치는 View02PollingScheduler 기동 — {@code enabled=true} 일 때만 돈다.
         */
        @DefaultValue("true") boolean bootstrapInfraOnStart
) {
    /** 원시 상태코드를 우리 enum 으로 변환(널/미정의 → UNKNOWN). */
    public ConnectionState resolveState(String rawCode) {
        if (rawCode == null || stateMap == null) {
            return ConnectionState.UNKNOWN;
        }
        return stateMap.getOrDefault(rawCode.trim(), ConnectionState.UNKNOWN);
    }
}
