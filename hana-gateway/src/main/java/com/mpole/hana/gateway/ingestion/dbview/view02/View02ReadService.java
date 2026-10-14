package com.mpole.hana.gateway.ingestion.dbview.view02;

import com.mpole.hana.common.dto.InfraStatus;
import com.mpole.hana.gateway.config.properties.View02Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;

/**
 * <b>외부 뷰 #02 읽기 — 중계기 네트워크 상태.</b>
 *
 * <p>SI 측 {@code vwDigitalTwin_02}(설정: {@code hdt.dbview.view02.table})에서 중계기 식별코드와
 * 원시 상태코드를 읽어 {@link InfraStatus} 로 변환한다. 원시 코드(예: 1/2/3)는
 * {@code hdt.dbview.view02.state-map} 설정으로 우리 {@code ConnectionState} 에 매핑되며,
 * 매핑에 없는 코드는 {@code UNKNOWN} 이 된다.</p>
 *
 * <h3>뷰 #01 과 다른 점</h3>
 * <ul>
 *   <li><b>변경감지(delta) 없음</b> — 상태는 "변경 이벤트"가 아니라 "현재 스냅샷"으로 다뤄야 한다.
 *       변경분만 보내면 소비자가 그 메시지를 놓쳤을 때(재기동 등) 재발행이 없어 영구 스테일이 된다.
 *       매 사이클 전량을 보내면 소비자가 DB 와 비교해 수렴하므로 유실이 구조적으로 사라진다.</li>
 *   <li><b>상태(state)를 갖지 않는다</b> — 뷰 #01 의 ReadService 는 인스턴스 필드(마지막 수집시각 등)를
 *       들고 있어 두 폴링이 같은 객체를 공유하면 안 된다. 그래서 클래스를 분리했다.</li>
 *   <li>행 수가 적어(수십~수백) 풀리드 비용이 무시할 수준이다.</li>
 * </ul>
 */
@Service
@Slf4j
public class View02ReadService {

    private final DatabaseClient mssqlDatabaseClient;
    private final View02Properties properties;

    // @Qualifier 는 생성자 파라미터에 둔다 — lombok.config 가 없어 @RequiredArgsConstructor 로는
    // 필드의 @Qualifier 가 생성자로 복사되지 않고, 이 모듈엔 DatabaseClient 빈이 2개(mssql/postgres)다.
    public View02ReadService(@Qualifier("mssqlDatabaseClient") DatabaseClient mssqlDatabaseClient,
                             View02Properties properties) {
        this.mssqlDatabaseClient = mssqlDatabaseClient;
        this.properties = properties;
    }

    /** 뷰 전량을 읽어 인프라 상태 스냅샷으로 변환한다. */
    public Flux<InfraStatus> readAll() {
        // 테이블/컬럼명은 식별자라 바인딩 파라미터로 넘길 수 없다 — yml 로만 관리되는 신뢰 입력.
        String sql = "SELECT %s AS infra_code, %s AS raw_state FROM %s WITH (NOLOCK)"
                .formatted(properties.codeColumn(), properties.stateColumn(), properties.table());

        return mssqlDatabaseClient.sql(sql)
                .map((row, meta) -> {
                    String code = row.get("infra_code", String.class);
                    Object rawState = row.get("raw_state");
                    String raw = rawState != null ? String.valueOf(rawState) : null;
                    return InfraStatus.builder()
                            .refInfraCode(code != null ? code.trim() : null)
                            .networkState(properties.resolveState(raw))
                            .updateDatetime(OffsetDateTime.now())
                            .build();
                })
                .all()
                // 식별코드가 없는 행은 매칭 대상이 없으므로 버린다.
                .filter(s -> s.refInfraCode() != null && !s.refInfraCode().isBlank())
                .doOnError(e -> log.error("[View02] {} 조회 실패: {}", properties.table(), e.getMessage()));
    }
}
