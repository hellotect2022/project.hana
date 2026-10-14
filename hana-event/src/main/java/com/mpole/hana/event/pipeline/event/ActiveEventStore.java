package com.mpole.hana.event.pipeline.event;

import com.mpole.hana.event.pipeline.event.dto.ActiveEvent;
import com.mpole.hana.event.pipeline.event.dto.ActiveEventRow;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 현재 활성(조건 성립 중) 이벤트의 인메모리 맵.
 *
 * <p><b>왜 필요한가</b> — Active 축의 중복 억제("활성 이벤트가 있으면 새로 발생시키지 않는다")를
 * 판정하려면 "이 규칙+포인트에 지금 떠 있는 이벤트가 있는가"를 평가 시점에 O(1) 로 알아야 한다.
 * 매 포인트마다 DB 를 조회할 수는 없으므로 메모리에 들고 있는다.</p>
 *
 * <p><b>DB 와의 관계</b> — 정본은 {@code tbl_event.cleared_at IS NULL}(미해소) 이고 이 맵은 그 캐시다. DB 에는
 * 부분 유니크 인덱스 {@code UNIQUE(rule_id, device_id, point_id) WHERE cleared_at IS NULL} 이 걸려 있어
 * 이 맵이 틀려도 중복 INSERT 는 DB 가 막는다(이중 안전장치).</p>
 *
 * <p><b>불변식</b> — 상태형 규칙의 열린 행(cleared_at IS NULL)은 반드시 이 맵에 같은 eventId 로 있어야 한다.
 * 메모리와 DB 는 별개 저장소라 밀리초 틈의 어긋남은 원리상 없앨 수 없으므로, 어긋남이 <i>드러나는</i> 순간
 * (INSERT 가 DB 에 막힘, 재활성이 0행/유니크 위반) {@link #resync} 로 DB 를 읽어 맵을 맞춘다(2026-10-06).
 * 이게 없으면 열린 행이 맵에 없는 키는 해소도 안 되고 INSERT 도 매번 막혀 그 장비가 영원히 알람을 못 띄운다.</p>
 *
 * <p><b>재기동 복원</b> — 프로세스가 죽어도 DB 의 활성 이벤트는 그대로 남는다. 기동 시 미확인 행을 복원하지 않으면
 * 이미 떠 있던 알람이 전부 다시 발생 통지되고, 해소·미확인 알람은 억제가 풀린다.</p>
 *
 * <p><b>주의</b> — {@code point_id} 가 NULL 인 이벤트는 PostgreSQL 유니크 인덱스가 NULL 을
 * 서로 다른 값으로 취급하므로 DB 차단이 걸리지 않는다. 이 맵은 NULL 도 하나의 키로 묶어
 * 그 구멍을 메운다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveEventStore {

    private final EventRuleReader reader;
    private final EventWriter writer;

    /** key = "{ruleId}|{deviceId}|{pointId}" → 활성 이벤트. */
    private final Map<String, ActiveEvent> active = new ConcurrentHashMap<>();

    private static String key(Long ruleId, Long deviceId, Long pointId) {
        return ActiveEvent.key(ruleId, deviceId, pointId);
    }

    /** 사이클 blocking 상한 — 초과 시 예외로 끊어 스케줄러 스레드 점유를 막는다. */
    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    /**
     * 기동 시 DB 의 활성 이벤트를 복원한다(블로킹). 실패하면 기동을 실패시키는 편이 낫다 —
     * 빈 맵으로 뜨면 이미 떠 있던 알람이 전부 다시 발생 통지된다.
     *
     * <p>순서(2026-10-06):</p>
     * <ol>
     *   <li>"확인됐는데 열린" 행({@code acked_at IS NOT NULL AND cleared_at IS NULL})을 먼저 닫는다. 확인=종료 모델에서
     *       있으면 안 되는 상태인데(10-02 이전 REST ack, 수동 UPDATE), 복원 조건이 {@code acked_at IS NULL} 이라 이런 행은
     *       맵에 안 들어오면서 유니크 인덱스만 살아 INSERT 를 막는다.</li>
     *   <li>미확인 행을 "열린 행이 마지막에 오도록" 정렬해 읽는다. 같은 키에 행이 여럿이면(과거 데이터·경합 잔재) 마지막 put 이
     *       남으므로 열린 행이 맵을 차지한다. occurred_at 순이었을 때는 재활성 행(occurred_at 이 최초 시각)이나 timestamp 가 임의인
     *       테스트 번들 때문에 해소된 행이 남고 열린 행이 고아가 됐다.</li>
     * </ol>
     */
    @PostConstruct
    public void restoreOnStartup() {
        Long closed = writer.closeAckedOpenRows().block(BLOCK_TIMEOUT);
        if (closed != null && closed > 0) {
            log.warn("[ActiveEventStore] 확인됐는데 열려 있던 행 {}건을 cleared_at=acked_at 으로 닫음(모델 위반 데이터 정리)", closed);
        }

        AtomicInteger dup = new AtomicInteger();
        reader.loadActiveEvents()
                .doOnNext(row -> {
                    ActiveEvent prev = active.put(key(row.ruleId(), row.deviceId(), row.pointId()), fromRow(row));
                    if (prev != null) {
                        dup.incrementAndGet();
                        log.warn("[ActiveEventStore] 같은 키에 미확인 행 중복 — 맵은 {} 를 채택(열린 행 우선), {} 는 이력으로만 남음. key={}",
                                row.eventId(), prev.eventId(), prev.key());
                    }
                })
                .then()
                .block();
        log.info("[ActiveEventStore] 활성 이벤트 복원 완료. count={}, 중복키={}\n{}", active.size(), dup.get(), dumpTable());
    }

    private static ActiveEvent fromRow(ActiveEventRow row) {
        return new ActiveEvent(row.eventId(), row.ruleId(), row.deviceId(), row.pointId(), row.level(),
                row.clearedAt() != null,  // 해소·미확인 → 재성립 시 재활성 대상
                row.ruleName(), row.eventCode(), row.deviceKey(), row.tagName(), row.pointName(),
                row.occurredAt(),
                row.lastOccurredAt() != null ? row.lastOccurredAt() : row.occurredAt());
    }

    /**
     * 보조 경로 — 웹소켓을 거치지 않고 DB 에서 직접 확인된 건(운영자 SQL, 다른 인스턴스 등)을 주기적으로 읽어 반영한다.
     * 기본 경로는 웹소켓 ACK({@link EventAckService}, 즉시 반영)이고, 이 스윕은 그걸 못 탄 확인을 최대 30초 안에 따라잡는다.
     * (digitaltwin REST {@code POST /ack} 는 2026-10-06 삭제 — 평시에 이 스윕이 잡을 건은 거의 없다.)
     *
     * <p>2026-10-06: 확인된 행은 DB 에서도 <b>닫는다</b>({@code cleared_at = COALESCE(cleared_at, acked_at)}). 예전처럼
     * acked_at 만 보고 맵에서 빼면 "확인됐는데 열린" 행이 DB 에 남아 그 키의 INSERT 가 영원히 막힌다.</p>
     * 미확인 항목이 없으면 쿼리조차 나가지 않아 평시 부하는 0 이다.
     */
    @Scheduled(
            fixedDelayString = "${hdt.event.ack-sweep-ms:30000}",
            initialDelayString = "${hdt.event.ack-sweep-ms:30000}",
            scheduler = "eventTaskScheduler"
    )
    public void sweep() {
        try {
            sweepAcked().block(BLOCK_TIMEOUT);
        } catch (Exception e) {
            log.error("[ActiveEventStore] ack 스윕 실패(다음 주기 재시도): {}", e.getMessage());
        }
    }

    private Mono<Void> sweepAcked() {
        List<UUID> unacked = active.values().stream()   // 맵에 있다 = 미확인
                .map(ActiveEvent::eventId)
                .toList();
        if (unacked.isEmpty()) {
            return Mono.empty();
        }
        return writer.closeAcked(unacked)
                .doOnNext(ackedId -> {
                    ActiveEvent e = applyAck(ackedId);
                    if (e != null) {
                        log.info("[ActiveEventStore] DB 직접 확인을 스윕으로 반영(종료). eventId={}", ackedId);
                    }
                })
                .then();
    }

    /** 지금 활성(또는 해소·미확인 대기) 상태인 항목. 없으면 null. */
    public ActiveEvent find(Long ruleId, Long deviceId, Long pointId) {
        return active.get(key(ruleId, deviceId, pointId));
    }

    /** 발생 등록. 이미 있으면 등록하지 않고 false 를 반환한다(경합 시 한쪽만 발생). {@code entry.awaitingAck} 는 false 여야 한다. */
    public boolean tryOccur(ActiveEvent entry) {
        return active.putIfAbsent(entry.key(), entry) == null;
    }

    /** 해소 등록 — 해소·미확인(②) 으로 남겨 확인 전까지 재발생을 억제한다. 확인된 건은 이미 맵에 없다. */
    public void clear(Long ruleId, Long deviceId, Long pointId) {
        active.computeIfPresent(key(ruleId, deviceId, pointId), (unused, e) -> e.withAwaitingAck());
    }

    /** 해소·미확인(②) 상태에서 조건이 다시 성립 → 같은 eventId 로 다시 활성(①). (2026-10-02 B안) */
    public void reactivate(Long ruleId, Long deviceId, Long pointId) {
        active.computeIfPresent(key(ruleId, deviceId, pointId), (unused, e) -> e.reactivated());
    }

    /**
     * 확인(ack) 반영 = <b>종료</b> — 웹소켓 ACK 와 스윕이 같이 쓴다. 조건 성립 여부와 무관하게 제거한다.
     * 이후 같은 조건이 (다시) 성립하면 새 eventId 로 발생한다(2026-10-02 결정: 확인 뒤에는 새 이벤트를 받는다).
     *
     * @return 제거된 항목. 맵에 없던 id(이미 종료·사건형·미지)면 null
     */
    public ActiveEvent applyAck(UUID eventId) {
        ActiveEvent found = active.values().stream()
                .filter(e -> eventId.equals(e.eventId()))
                .findFirst().orElse(null);
        if (found == null) return null;
        active.remove(found.key(), found);
        return found;
    }

    /** 발생 등록을 되돌린다(정본 INSERT 실패 등). 이후 같은 조건이 다시 성립하면 새로 발생한다. */
    public void release(Long ruleId, Long deviceId, Long pointId) {
        active.remove(key(ruleId, deviceId, pointId));
    }

    /** {@link #resync} 의 결과 — 맵을 DB 에 어떻게 맞췄는지. */
    public enum ResyncAction {
        /** DB 의 열린 미확인 행을 맵에 들여옴(이후 해소·재활성이 그 행으로 정상 동작). */
        ADOPTED,
        /** DB 의 열린 행이 이미 확인된 것이라 닫음. 맵은 비움 — 호출부는 INSERT 를 한 번 재시도해도 된다. */
        CLOSED_ACKED,
        /** DB 에 열린 행 없음. 맵만 비움. */
        NONE
    }

    public record Resync(ResyncAction action, ActiveEvent entry) {
    }

    /**
     * 맵을 DB 에 맞춘다 — 어긋남이 드러났을 때(INSERT 가 DB 에 막힘, 재활성 0행/유니크 위반) 호출한다(2026-10-06).
     *
     * <pre>
     *   DB 열린 행 없음              → 맵 제거.                      다음 성립은 새 이벤트
     *   DB 열린 행 있음·미확인       → 그 행을 맵에 "활성" 으로 채택.  해소·재활성이 그 행으로 이어진다
     *   DB 열린 행 있음·확인됨       → cleared_at=acked_at 으로 닫고 맵 제거. 호출부가 INSERT 재시도 가능
     * </pre>
     * 맵에 있던 다른 eventId(해소·미확인 등)는 DB 에서 그대로 이력으로 남는다.
     */
    public Mono<Resync> resync(Long ruleId, Long deviceId, Long pointId) {
        String k = key(ruleId, deviceId, pointId);
        return reader.findOpenEvent(ruleId, deviceId, pointId)
                .flatMap(row -> {
                    if (row.ackedAt() != null) {
                        return writer.closeAckedOpenRow(row.eventId())
                                .doOnNext(n -> {
                                    active.remove(k);
                                    log.warn("[ActiveEventStore] 재동기화 — 확인됐는데 열려 있던 행 닫음. key={}, eventId={}", k, row.eventId());
                                })
                                .thenReturn(new Resync(ResyncAction.CLOSED_ACKED, null));
                    }
                    ActiveEvent adopted = fromRow(row).reactivated(); // 열린 행이므로 awaitingAck=false, updatedAt=now
                    ActiveEvent prev = active.put(k, adopted);
                    log.warn("[ActiveEventStore] 재동기화 — DB 열린 행을 맵에 채택. key={}, eventId={}, 이전 맵={}",
                            k, adopted.eventId(), prev == null ? "없음" : prev.eventId() + "(" + prev.stateLabel() + ")");
                    return Mono.just(new Resync(ResyncAction.ADOPTED, adopted));
                })
                .switchIfEmpty(Mono.fromCallable(() -> {
                    ActiveEvent prev = active.remove(k);
                    log.warn("[ActiveEventStore] 재동기화 — DB 에 열린 행 없음, 맵 제거. key={}, 이전 맵={}",
                            k, prev == null ? "없음" : prev.eventId());
                    return new Resync(ResyncAction.NONE, null);
                }));
    }

    public int size() {
        return active.size();
    }

    /**
     * 개발자용 — 활성맵 전체를 고정폭 격자 문자열로 만든다(2026-10-06). 발생·해소·재활성·확인 직후 파이프라인이 로그로 찍는다.
     * 정렬은 마지막 전이 시각 내림차순(방금 바뀐 것이 맨 위). 항목이 없으면 한 줄짜리 안내만 돌려준다.
     */
    public String dumpTable() {
        List<ActiveEvent> rows = active.values().stream()
                .sorted(Comparator.comparing(ActiveEvent::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        long live = rows.stream().filter(e -> !e.awaitingAck()).count();
        StringBuilder sb = new StringBuilder(256 + rows.size() * 200);
        sb.append("┌─ 활성맵 ").append(rows.size()).append("건 (활성 ").append(live)
                .append(" · 해소·미확인 ").append(rows.size() - live).append(") ─\n");
        if (rows.isEmpty()) {
            sb.append("└ (비어 있음)");
            return sb.toString();
        }
        String fmt = "│ %-11s %-36s %7s %-16s %-12s %9s %-22s %8s %-12s %-16s %-8s %-15s %-15s%n";
        sb.append(String.format(fmt, "state", "event_id", "rule_id", "rule_name", "event_code",
                "device_id", "device_key", "point_id", "tag_name", "point_name", "level", "occurred_at", "updated_at"));
        for (ActiveEvent e : rows) {
            sb.append(String.format(fmt,
                    e.stateLabel(), e.eventId(), e.ruleId(), cut(e.ruleName(), 16), cut(e.eventCode(), 12),
                    e.deviceId(), cut(e.deviceKey(), 22), e.pointId(), cut(e.tagName(), 12), cut(e.pointName(), 16),
                    e.level(), ts(e.occurredAt()), ts(e.updatedAt())));
        }
        sb.append("└");
        return sb.toString();
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private static String ts(OffsetDateTime t) {
        return t == null ? "-" : TS.format(t.atZoneSameInstant(ZoneId.systemDefault()));
    }

    private static String cut(String s, int max) {
        if (s == null) return "-";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
