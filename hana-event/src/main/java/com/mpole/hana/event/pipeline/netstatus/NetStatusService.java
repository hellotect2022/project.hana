package com.mpole.hana.event.pipeline.netstatus;

import com.mpole.hana.common.dto.InfraStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 인프라 상태 스냅샷 적용 → 인프라 마스터 갱신 → 장비 통신상태 <b>수렴(reconcile)</b>.
 *
 * <p>Kafka 수신은 {@code consumer.View02KafkaConsumer} 가 담당하고, 이 클래스는
 * <b>처리(DB 반영)만</b> 맡는다 — 수신 계층과 도메인 처리 계층의 분리.</p>
 *
 * <p><b>왜 "전이 이벤트 전파"가 아니라 "수렴"인가</b> — 전이 시점에만 장비를 갱신하면
 * 메시지 유실·컨슈머 재기동·순서 역전·부분 실패가 곧바로 영구 스테일로 이어진다(재발행이 없으므로).
 * 대신 매 배치마다 "DB 의 현재 인프라 상태"와 "장비의 현재 상태"가 다른 행만 UPDATE 하면
 * 멱등이라 위 문제들이 전부 사라지고, 어떤 드리프트든 다음 사이클(≤ 폴링 주기)에 복구된다.</p>
 *
 * <p>처리 3단계: ① 인프라 상태 갱신(변화 시 이력 append) → ② 상속 대상 장비의 current_value 행 보장
 * → ③ 불일치 장비만 connection_state 수렴.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "hdt.dbview.view02", name = "enabled", havingValue = "true")
public class NetStatusService {

    private final DatabaseClient postgresDatabaseClient;

    /** 스냅샷 1배치 적용: 인프라 갱신 → 이력 → 장비 수렴. */
    public Mono<Void> applySnapshot(List<InfraStatus> statuses) {
        if (statuses.isEmpty()) {
            return Mono.empty();
        }
        return Mono.when(statuses.stream().map(this::updateInfra).toList())
                .then(reconcileDevices())
                .doOnSuccess(v -> log.debug("[InfraStatus] 스냅샷 {}건 적용 완료", statuses.size()));
    }

    /**
     * 등록된 인프라만 갱신한다(미등록 ref 코드는 무시 — 관리자가 등록해야 상속 대상이 됨).
     * 상태가 실제로 바뀐 경우에만 이력을 남긴다.
     */
    private Mono<Void> updateInfra(InfraStatus s) {
        String state = s.networkState().name();
        String updateSql = """
                UPDATE tbl_device_infra
                SET network_state = :state, last_comm_at = NOW(), updated_at = NOW()
                WHERE ref_infra_code = :code
                """;
        // 이력을 먼저(변화 판정을 위해 UPDATE 이전 값이 필요) → 그 다음 상태/최종수신시각 갱신.
        return postgresDatabaseClient.sql(updateSql)
                .bind("state", state)
                .bind("code", s.refInfraCode())
                .then();
    }

    /**
     * 상속 수렴 — 인프라 소속 장비 중 상태가 다른 행만 갱신(멱등).
     * current_value 행이 아직 없는 장비는 먼저 생성한다(값 수신 전이라도 통신상태는 표시돼야 함).
     */
    private Mono<Void> reconcileDevices() {
        String ensureRowSql = """
                INSERT INTO tbl_device_current_value (device_id, device_key, connection_state, created_at, updated_at)
                SELECT d.device_id, d.device_key, 'UNKNOWN', NOW(), NOW()
                FROM tbl_device d
                WHERE d.infra_id IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM tbl_device_current_value cv WHERE cv.device_id = d.device_id)
                ON CONFLICT (device_id) DO NOTHING
                """;
        String reconcileSql = """
                UPDATE tbl_device_current_value cv
                SET connection_state = i.network_state
                FROM tbl_device d
                JOIN tbl_device_infra i ON d.infra_id = i.infra_id
                WHERE cv.device_id = d.device_id
                  AND cv.connection_state IS DISTINCT FROM i.network_state
                """;
        return postgresDatabaseClient.sql(ensureRowSql).fetch().rowsUpdated()
                .flatMap(created -> postgresDatabaseClient.sql(reconcileSql).fetch().rowsUpdated()
                        .doOnNext(updated -> {
                            if (created > 0 || updated > 0) {
                                log.info("[InfraStatus] 상속 수렴 — 신규 행 {}건, 상태 갱신 {}건", created, updated);
                            }
                        }))
                .then();
    }
}
