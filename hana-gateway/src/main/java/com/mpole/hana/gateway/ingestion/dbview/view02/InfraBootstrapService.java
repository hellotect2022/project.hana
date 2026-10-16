package com.mpole.hana.gateway.ingestion.dbview.view02;

import com.mpole.hana.gateway.config.properties.View02Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;

/**
 * <b>기동 시 인프라 마스터 동기화</b> — {@code vwDigitalTwin_02} 의 장치 목록을 {@code tbl_device_infra} 에 upsert 한다 (2026-10-16).
 *
 * <pre>
 *   신규(ref_infra_code 없음) INSERT : infra_name = DEVICE_NAME(비면 코드), infra_type = REPEATER,
 *                                     network_state = UNKNOWN, active = true, properties = {"source":"vwDigitalTwin_02"}
 *   기존                       UPDATE : infra_name ← DEVICE_NAME(비면 그대로), properties 에 source 만 병합(다른 키 보존)
 *                                     — infra_type·active·network_state·infra_display_name 은 건드리지 않는다(관리자 값 보존)
 * </pre>
 *
 * <p>값이 실제로 달라진 행만 UPDATE 한다. 뷰에서 사라진 코드는 지우지 않는다(장비가 참조 중일 수 있음).
 * 2026-09-16 일회성 스크립트({@code 05-08.hanadream_db/insert_infra_from_vw02.py})를 기동 시점으로 옮긴 것.</p>
 *
 * <p>SQL 은 열마다 배열 1개를 {@code unnest} 로 펼쳐 행 수와 무관하게 고정 문자열이다.</p>
 */
@Service
@Slf4j
public class InfraBootstrapService {

    private static final String SOURCE_JSON = "{\"source\":\"vwDigitalTwin_02\"}";

    /** 신규만 INSERT — 기존 행은 DO NOTHING(아래 UPDATE 가 처리). */
    private static final String INSERT_NEW = """
            INSERT INTO tbl_device_infra
                   (ref_infra_code, infra_name, infra_type, network_state, active, properties, created_at, updated_at)
            SELECT u.code, COALESCE(NULLIF(u.name, ''), u.code), 'REPEATER', 'UNKNOWN', true, CAST(:src AS jsonb), now(), now()
              FROM unnest(CAST(:code AS text[]), CAST(:name AS text[])) AS u(code, name)
            ON CONFLICT (ref_infra_code) DO NOTHING
            RETURNING infra_id
            """;

    /** 기존 행 — 이름이 다르거나 source 표시가 없을 때만 갱신. */
    private static final String UPDATE_EXISTING = """
            UPDATE tbl_device_infra t
               SET infra_name = COALESCE(NULLIF(u.name, ''), t.infra_name),
                   properties = COALESCE(t.properties, '{}'::jsonb) || CAST(:src AS jsonb),
                   updated_at = now()
              FROM unnest(CAST(:code AS text[]), CAST(:name AS text[])) AS u(code, name)
             WHERE t.ref_infra_code = u.code
               AND (t.infra_name IS DISTINCT FROM COALESCE(NULLIF(u.name, ''), t.infra_name)
                    OR NOT (COALESCE(t.properties, '{}'::jsonb) @> CAST(:src AS jsonb)))
            RETURNING t.infra_id
            """;

    private final DatabaseClient mssqlDatabaseClient;
    private final DatabaseClient postgresDatabaseClient;
    private final View02Properties properties;

    public InfraBootstrapService(@Qualifier("mssqlDatabaseClient") DatabaseClient mssqlDatabaseClient,
                                 @Qualifier("postgresDatabaseClient") DatabaseClient postgresDatabaseClient,
                                 View02Properties properties) {
        this.mssqlDatabaseClient = mssqlDatabaseClient;
        this.postgresDatabaseClient = postgresDatabaseClient;
        this.properties = properties;
    }

    /** 뷰 전량 읽기 → 신규 INSERT → 기존 UPDATE. 결과는 로그로 남긴다. */
    public Mono<Void> syncFromView() {
        long start = System.currentTimeMillis();
        // 테이블/컬럼명은 식별자라 바인딩할 수 없다 — yml 로만 관리되는 신뢰 입력.
        String sql = "SELECT DEVICE_CODE, DEVICE_NAME, STATUS FROM %s WITH (NOLOCK)"
                .formatted(properties.table());

        return mssqlDatabaseClient.sql(sql)
                .map((row, meta) -> new String[]{
                        trim(row.get("DEVICE_CODE", String.class)),
                        trim(row.get("DEVICE_NAME", String.class))
                })
                .all()
                .filter(r -> r[0] != null)
                // 코드 기준 중복 제거(뷰는 UNIQUE 지만 방어적으로)
                .collect(LinkedHashMap<String, String>::new, (m, r) -> m.putIfAbsent(r[0], r[1] == null ? "" : r[1]))
                .flatMap(byCode -> {
                    if (byCode.isEmpty()) {
                        log.warn("[InfraBootstrap] {} 에 행이 없어 동기화할 인프라가 없습니다.", properties.table());
                        return Mono.empty();
                    }
                    String[] codes = byCode.keySet().toArray(new String[0]);
                    String[] names = byCode.values().toArray(new String[0]);
                    return run(INSERT_NEW, codes, names)
                            .flatMap(inserted -> run(UPDATE_EXISTING, codes, names)
                                    .doOnNext(updated -> log.info(
                                            "[InfraBootstrap] {} → tbl_device_infra 동기화: 뷰 {}건, 신규 {}건, 갱신 {}건, 변화 없음 {}건 ({}ms)",
                                            properties.table(), codes.length, inserted, updated,
                                            codes.length - inserted - updated, System.currentTimeMillis() - start)))
                            .then();
                });
    }

    private Mono<Long> run(String sql, String[] codes, String[] names) {
        return postgresDatabaseClient.sql(sql)
                .bind("code", codes)
                .bind("name", names)
                .bind("src", SOURCE_JSON)
                .map((row, meta) -> 1)
                .all()
                .count();
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
