package com.mpole.hana.event.pipeline.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpole.hana.common.dto.EventClear;
import com.mpole.hana.common.dto.EventOccurrence;
import io.r2dbc.spi.Parameter;
import io.r2dbc.spi.Parameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * 이벤트 정본({@code tbl_event})의 발생 INSERT · 해소 UPDATE.
 *
 * <p>이 테이블 스키마는 digitaltwin(JPA ddl-auto)이 소유하지만 실제 쓰기는 event-server(R2DBC)가
 * 담당한다(설계: 이벤트 정본 일원화). {@code event_id} 는 평가기가 만든 UUIDv7 이므로 INSERT 응답을
 * 기다려 PK 를 받아올 필요가 없다.</p>
 *
 * <p>JPA 를 우회하는 raw SQL 이므로 created_at/updated_at 을 명시 세팅한다
 * (DateEntity 의 @PrePersist 가 동작하지 않음).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventWriter {

    private final DatabaseClient postgresDatabaseClient;
    private final ObjectMapper objectMapper;

    /**
     * 발생 INSERT. {@code ON CONFLICT DO NOTHING} 은 부분 유니크 인덱스
     * {@code uk_event_open (rule_id, device_id, point_id) WHERE cleared_at IS NULL} 를 대상으로 하며,
     * 인메모리 활성 맵이 놓친 중복(다중 인스턴스 등)을 DB 가 마지막으로 막는다.
     */
    private static final String INSERT_SQL = """
            INSERT INTO tbl_event
             (event_id, rule_id, rule_name, event_code, level, scope, device_id, device_key, point_id, tag_name,
              point_type, value_raw, threshold_value, operator, trigger_value,
              outputs, icon, effect, occurred_at, cleared_at, acked_at, occurrence_count, last_occurred_at)
            VALUES
             (:eventId, :ruleId, :ruleName, :eventCode, :level, :scope, :deviceId, :deviceKey, :pointId, :tagName,
              :pointType, :valueRaw, :thresholdValue, :operator, :triggerValue,
              :outputs::jsonb, :icon, :effect, :occurredAt, :clearedAt, :ackedAt, 1, :occurredAt)
            ON CONFLICT (rule_id, device_id, point_id) WHERE cleared_at IS NULL DO NOTHING
            """;

    /**
     * 재활성 UPDATE(2026-10-02 B안) — 해소·미확인 이벤트에 조건이 다시 성립. 새 행 대신 같은 행을 다시 연다.
     * {@code acked_at IS NULL} 조건으로 확인된 건은 건드리지 않는다(그 경우 메모리와 어긋난 것 — 호출부가 슬롯을 푼다).
     */
    private static final String REACTIVATE_SQL = """
            UPDATE tbl_event
               SET cleared_at = NULL,
                   cleared_value = NULL,
                   occurrence_count = occurrence_count + 1,
                   last_occurred_at = :occurredAt,
                   value_raw = :valueRaw
             WHERE event_id = :eventId AND cleared_at IS NOT NULL AND acked_at IS NULL
             RETURNING occurrence_count
            """;

    /**
     * 확인 UPDATE — 최초 확인만 남긴다. event_code 를 돌려받아 {@code {code}_ACK} 봉투에 쓴다.
     * <p>확인 = 종료(2026-10-02): 아직 해소되지 않았으면 {@code cleared_at} 도 확인 시각으로 닫는다.
     * 그래야 부분 유니크 {@code uk_event_open} 이 풀려 다음 성립이 새 행으로 들어간다.</p>
     */
    private static final String ACK_SQL = """
            UPDATE tbl_event
               SET acked_at = :ackedAt, acked_by = :ackedBy,
                   cleared_at = COALESCE(cleared_at, :ackedAt)
             WHERE event_id = :eventId AND acked_at IS NULL
             RETURNING event_code
            """;

    /**
     * 해소 UPDATE. {@code AND cleared_at IS NULL} 조건으로 이미 해소된 건을 두 번 내리지 않는다
     * (해소 시각·값은 최초 해소 것을 보존).
     *
     * <p>2026-10-01 — {@code active} 컬럼 삭제. 활성 여부는 {@code cleared_at IS NULL} 하나로 판정한다
     * (boolean 과 시각이 중복이었다). {@code created_at}/{@code updated_at} 도 삭제 — occurred_at/cleared_at/acked_at 이 대신한다.</p>
     */
    private static final String CLEAR_SQL = """
            UPDATE tbl_event
               SET cleared_at = :clearedAt,
                   cleared_value = :clearedValue
             WHERE event_id = :eventId AND cleared_at IS NULL
            """;

    /**
     * 정본 INSERT. 실제로 저장됐으면 true, 유니크 충돌로 무시됐으면 false.
     *
     * @param stateless 사건형(suppress_mode=NONE). true 면 {@code cleared_at=occurred_at} 로 바로 닫히고
     *                  {@code acked_at=occurred_at, acked_by=NULL} 로 <b>자동 완료</b>된다(2026-10-02 결정 —
     *                  버튼 요청은 DT 에서 조치할 것이 없으므로 발행 순간 완료. acked_by NULL = 시스템).
     *                  부분 유니크 {@code uk_event_open} 은 미해소 행만 보므로 같은 버튼을 연타해도 행이 각각 남는다.
     */
    public Mono<Boolean> insert(EventOccurrence ev, boolean stateless) {
        String outputsJson = serializeOutputs(ev);

        return postgresDatabaseClient.sql(INSERT_SQL)
                .bind("eventId", ev.eventId())
                .bind("clearedAt", p(stateless ? ev.occurredAt() : null, OffsetDateTime.class))
                .bind("ackedAt", p(stateless ? ev.occurredAt() : null, OffsetDateTime.class))
                .bind("ruleId", p(ev.ruleId(), Long.class))
                .bind("ruleName", p(ev.ruleName(), String.class))
                .bind("eventCode", p(ev.eventCode(), String.class))
                .bind("level", p(ev.level(), String.class))
                .bind("scope", p(ev.scope(), String.class))
                .bind("deviceId", p(ev.deviceId(), Long.class))
                .bind("deviceKey", p(ev.deviceKey(), String.class))
                .bind("pointId", p(ev.pointId(), Long.class))
                .bind("tagName", p(ev.tagName(), String.class))
                .bind("pointType", p(ev.pointType(), String.class))
                .bind("valueRaw", p(ev.valueRaw(), String.class))
                .bind("thresholdValue", p(ev.thresholdValue(), Double.class))
                .bind("operator", p(ev.operator(), String.class))
                .bind("triggerValue", p(ev.triggerValue(), String.class))
                .bind("outputs", p(outputsJson, String.class))
                .bind("icon", p(ev.icon(), String.class))
                .bind("effect", p(ev.effect(), String.class))
                .bind("occurredAt", p(ev.occurredAt(), OffsetDateTime.class))
                .fetch()
                .rowsUpdated()
                .map(rows -> rows > 0)
                .doOnNext(inserted -> {
                    if (!inserted) {
                        log.debug("[EventWriter] 활성 중복으로 INSERT 무시. ruleId={}, deviceId={}, pointId={}",
                                ev.ruleId(), ev.deviceId(), ev.pointId());
                    }
                })
                .doOnError(e -> log.error("[EventWriter] tbl_event INSERT 실패: {}", e.getMessage()));
    }

    /**
     * 해소 UPDATE. 실제로 내려간 행이 있으면 true.
     */
    public Mono<Boolean> clear(EventClear ev) {
        return postgresDatabaseClient.sql(CLEAR_SQL)
                .bind("eventId", ev.eventId())
                .bind("clearedAt", p(ev.clearedAt(), OffsetDateTime.class))
                .bind("clearedValue", p(ev.clearedValue(), String.class))
                .fetch()
                .rowsUpdated()
                .map(rows -> rows > 0)
                .doOnError(e -> log.error("[EventWriter] tbl_event 해소 UPDATE 실패: {}", e.getMessage()));
    }

    /**
     * 재활성. 갱신된 {@code occurrence_count} 를 돌려준다. 대상 행이 없으면(이미 확인됐거나 해소 상태가 아님) empty.
     */
    public Mono<Integer> reactivate(UUID eventId, OffsetDateTime occurredAt, String valueRaw) {
        return postgresDatabaseClient.sql(REACTIVATE_SQL)
                .bind("eventId", eventId)
                .bind("occurredAt", p(occurredAt, OffsetDateTime.class))
                .bind("valueRaw", p(valueRaw, String.class))
                .map((row, meta) -> row.get("occurrence_count", Integer.class))
                .one()
                .doOnError(e -> log.error("[EventWriter] tbl_event 재활성 UPDATE 실패: {}", e.getMessage()));
    }

    /**
     * 확인. 실제로 확인 처리된 행의 event_code(없으면 Optional.empty 안의 null 대신 빈 Optional)를 돌려준다.
     * 이미 확인됐거나 없는 id 면 empty Mono.
     */
    public Mono<Optional<String>> ack(UUID eventId, Long userId, OffsetDateTime ackedAt) {
        return postgresDatabaseClient.sql(ACK_SQL)
                .bind("eventId", eventId)
                .bind("ackedAt", p(ackedAt, OffsetDateTime.class))
                .bind("ackedBy", p(userId, Long.class))
                .map((row, meta) -> Optional.ofNullable(row.get("event_code", String.class)))
                .one()
                .doOnError(e -> log.error("[EventWriter] tbl_event 확인 UPDATE 실패: {}", e.getMessage()));
    }

    /**
     * "확인됐는데 열린" 행 전부 닫기 — {@code cleared_at = acked_at}. 확인=종료 모델에서 있으면 안 되는 상태의 정리(2026-10-06).
     * 기동 복원 직전에 한 번 돈다. 10-02 이전 REST ack(cleared_at 을 안 닫았음)·수동 UPDATE 잔재가 대상이고, 정상 운영에서는 0행.
     *
     * @return 닫은 행 수
     */
    public Mono<Long> closeAckedOpenRows() {
        return postgresDatabaseClient.sql("""
                        UPDATE tbl_event SET cleared_at = acked_at
                         WHERE acked_at IS NOT NULL AND cleared_at IS NULL
                        """)
                .fetch()
                .rowsUpdated()
                .doOnError(e -> log.error("[EventWriter] 확인됐는데 열린 행 닫기 실패: {}", e.getMessage()));
    }

    /** 위와 같되 한 행만 — 맵 재동기화({@code ActiveEventStore#resync})에서 쓴다. 대상이 아니면 0. */
    public Mono<Long> closeAckedOpenRow(UUID eventId) {
        return postgresDatabaseClient.sql("""
                        UPDATE tbl_event SET cleared_at = acked_at
                         WHERE event_id = :eventId AND acked_at IS NOT NULL AND cleared_at IS NULL
                        """)
                .bind("eventId", eventId)
                .fetch()
                .rowsUpdated()
                .doOnError(e -> log.error("[EventWriter] 확인됐는데 열린 행 닫기 실패. eventId={}: {}", eventId, e.getMessage()));
    }

    /**
     * 스윕용 — 주어진 id 중 DB 에서 확인(acked_at)된 행을 돌려주면서, 아직 열려 있으면 <b>닫기도 한다</b>
     * ({@code cleared_at = COALESCE(cleared_at, acked_at)}). 2026-10-06: 예전 {@code findAckedEventIds} 는 읽기만 해서,
     * acked_at 만 채워진 행이 맵에서는 빠지고 DB 에서는 열린 채 남아 그 키의 INSERT 가 영원히 막혔다.
     *
     * @return 확인된(= 종료된) event_id 들
     */
    public Flux<UUID> closeAcked(Collection<UUID> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Flux.empty();
        }
        return postgresDatabaseClient.sql("""
                        UPDATE tbl_event SET cleared_at = COALESCE(cleared_at, acked_at)
                         WHERE event_id = ANY(:ids) AND acked_at IS NOT NULL
                         RETURNING event_id
                        """)
                .bind("ids", eventIds.toArray(new UUID[0]))
                .map((row, meta) -> row.get("event_id", UUID.class))
                .all()
                .doOnError(e -> log.error("[EventWriter] 스윕 UPDATE 실패: {}", e.getMessage()));
    }

    /** nullable 바인딩: 값이 있으면 그대로, null 이면 타입 지정 null 파라미터. */
    private static Parameter p(Object value, Class<?> type) {
        return value != null ? Parameters.in(value) : Parameters.in(type);
    }

    /** outputs 를 JSONB 문자열로 직렬화. null 이면 null(액션 없는 event). */
    private String serializeOutputs(EventOccurrence ev) {
        if (ev.outputs() == null) return null;
        try {
            return objectMapper.writeValueAsString(ev.outputs());
        } catch (Exception e) {
            log.warn("[EventWriter] outputs 직렬화 실패(null 저장): {}", e.getMessage());
            return null;
        }
    }
}
