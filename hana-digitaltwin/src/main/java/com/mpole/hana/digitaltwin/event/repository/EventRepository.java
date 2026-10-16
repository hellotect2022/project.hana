package com.mpole.hana.digitaltwin.event.repository;

import com.mpole.hana.digitaltwin.event.dto.AlarmHistoryRow;
import com.mpole.hana.digitaltwin.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 이벤트 정본 조회. 실제 이벤트의 쓰기(발생/해소/재활성/확인)는 전부 event-server(R2DBC)가 담당한다.
 * (2026-10-06: 원시 로그 {@code findByOptionalFilters} 와 REST 확인 {@code ack} 삭제 — 웹소켓 ACK 로 단일화)
 * <p>예외는 <b>모의 이벤트</b>({@code simulated = true}, 2026-10-07)다. SOP 시나리오 훈련용 행은 digitaltwin 이 직접 INSERT·확인한다.
 * event 서버는 그 행을 읽지 않는다. 아래 "모의 이벤트" 절의 메서드는 전부 {@code simulated = TRUE} 조건이 걸려 실제 행을 건드리지 못한다.</p>
 */
public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * <b>알람 이력 화면 전용 조회</b> — 명칭(장비명)·위치(층)·알람명·완료여부·발생일시.
     *
     * <p>{@code Event} 는 {@code deviceId} 를 연관관계 없이 Long 으로만 들고 있어 엔티티 조인(ON)으로 붙인다.
     * 표시에 필요한 장비명·층·카테고리는 <b>적재 시점에 비정규화하지 않고 조회 시 조인</b>한다 —
     * 컬럼 추가 없이 만들 수 있고, 이벤트 적재(hana-event)를 건드리지 않기 위해서다.
     * 대신 장비를 다른 층으로 옮기면 과거 이력의 위치도 함께 바뀐다(현재 위치 기준).</p>
     *
     * <p>선택 필터의 {@code CAST(... IS NULL)} 은 파라미터 타입추론 실패를 막기 위한 것이다
     * (2026-08-24 JPQL IS NULL 캐스트 수정 보고서 참고).</p>
     *
     * @param categoryIds 소분류 id 목록. null 이면 전체(빈 목록을 넘기지 말 것 — 결과가 0건이 된다)
     * @param completed   true=완료(ackedAt 존재 — 관제자 확인), false=미완료(미확인), null=전체.
     *                    2026-10-02 기획 확정: 알람을 닫는 유일한 행위가 확인. 클라이언트 기동 복구가 completed=false 를 쓴다.
     * @param simulated   true=모의(SOP 훈련)만, false=실제만, null=전체. 2026-10-07
     */
    @Query("""
            SELECT new com.mpole.hana.digitaltwin.event.dto.AlarmHistoryRow(
                       e.eventId, e.ruleId, e.ruleName, e.eventCode, e.level,
                       e.deviceId, d.deviceName, e.deviceKey,
                       f.floorName, z.zoneName,
                       c.categoryId, c.categoryName,
                       e.occurredAt, e.clearedAt, e.ackedAt, e.occurrenceCount, e.simulated, e.sopEnabled)
            FROM Event e
            LEFT JOIN Device d ON d.deviceId = e.deviceId
            LEFT JOIN DeviceCategory c ON c.categoryId = d.category.categoryId
            LEFT JOIN DevicePlacement p ON p.device.deviceId = d.deviceId
            LEFT JOIN UnityLocZone z ON z.zoneId = p.unityZone.zoneId
            LEFT JOIN UnityLocFloor f ON f.floorId = z.floor.floorId
            WHERE (CAST(:level AS string) IS NULL OR e.level = :level)
              AND (CAST(:ruleId AS long) IS NULL OR e.ruleId = :ruleId)
              AND (CAST(:deviceId AS long) IS NULL OR e.deviceId = :deviceId)
              AND (CAST(:floorId AS long) IS NULL OR f.floorId = :floorId)
              AND (:categoryIds IS NULL OR c.categoryId IN :categoryIds)
              AND (CAST(:completed AS boolean) IS NULL
                   OR (:completed = TRUE  AND e.ackedAt IS NOT NULL)
                   OR (:completed = FALSE AND e.ackedAt IS NULL))
              AND (CAST(:simulated AS boolean) IS NULL OR e.simulated = :simulated)
              AND (CAST(:qLike AS string) IS NULL
                   OR lower(e.ruleName)   LIKE :qLike
                   OR lower(d.deviceName) LIKE :qLike
                   OR lower(e.deviceKey)  LIKE :qLike)
              AND (CAST(:from AS timestamp) IS NULL OR e.occurredAt >= :from)
              AND (CAST(:to   AS timestamp) IS NULL OR e.occurredAt <= :to)
            """)
    Page<AlarmHistoryRow> searchAlarmHistory(
            @Param("level") String level,
            @Param("ruleId") Long ruleId,
            @Param("deviceId") Long deviceId,
            @Param("floorId") Long floorId,
            @Param("categoryIds") Collection<Long> categoryIds,
            @Param("completed") Boolean completed,
            @Param("simulated") Boolean simulated,
            @Param("qLike") String qLike,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );

    /**
     * 이력 화면 상단 집계 — 같은 필터로 전체/완료(확인) 건수. (미완료 = 전체 − 완료)
     * <p>{@code searchAlarmHistory} 와 조건이 반드시 같아야 표와 숫자가 맞는다. 2026-10-01 추가, 10-02 ackedAt 기준.</p>
     *
     * @return {@code [total(Long), completed(Long)]}
     */
    @Query("""
            SELECT count(e),
                   COALESCE(SUM(CASE WHEN e.ackedAt IS NOT NULL THEN 1 ELSE 0 END), 0)
            FROM Event e
            LEFT JOIN Device d ON d.deviceId = e.deviceId
            LEFT JOIN DeviceCategory c ON c.categoryId = d.category.categoryId
            LEFT JOIN DevicePlacement p ON p.device.deviceId = d.deviceId
            LEFT JOIN UnityLocZone z ON z.zoneId = p.unityZone.zoneId
            LEFT JOIN UnityLocFloor f ON f.floorId = z.floor.floorId
            WHERE (CAST(:level AS string) IS NULL OR e.level = :level)
              AND (CAST(:ruleId AS long) IS NULL OR e.ruleId = :ruleId)
              AND (CAST(:deviceId AS long) IS NULL OR e.deviceId = :deviceId)
              AND (CAST(:floorId AS long) IS NULL OR f.floorId = :floorId)
              AND (:categoryIds IS NULL OR c.categoryId IN :categoryIds)
              AND (CAST(:simulated AS boolean) IS NULL OR e.simulated = :simulated)
              AND (CAST(:qLike AS string) IS NULL
                   OR lower(e.ruleName)   LIKE :qLike
                   OR lower(d.deviceName) LIKE :qLike
                   OR lower(e.deviceKey)  LIKE :qLike)
              AND (CAST(:from AS timestamp) IS NULL OR e.occurredAt >= :from)
              AND (CAST(:to   AS timestamp) IS NULL OR e.occurredAt <= :to)
            """)
    Object[] countAlarmHistory(
            @Param("level") String level,
            @Param("ruleId") Long ruleId,
            @Param("deviceId") Long deviceId,
            @Param("floorId") Long floorId,
            @Param("categoryIds") Collection<Long> categoryIds,
            @Param("simulated") Boolean simulated,
            @Param("qLike") String qLike,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    /* ───────────────────── 모의 이벤트 (SOP 시나리오 훈련, 2026-10-07) ─────────────────────
     * digitaltwin 이 tbl_event 에 쓰는 유일한 경로. 전부 simulated = TRUE 행만 다루므로 실제 이벤트(event 서버 소유)는 건드리지 않는다.
     */

    /** 같은 규칙+장비+포인트의 열린 모의 행(uk_event_sim_open 으로 최대 1건). 같은 장비를 다시 클릭했을 때 기존 id 반환용. */
    @Query("""
            SELECT e FROM Event e
             WHERE e.simulated = TRUE AND e.clearedAt IS NULL
               AND e.ruleId = :ruleId AND e.deviceId = :deviceId
               AND ((CAST(:pointId AS long) IS NULL AND e.pointId IS NULL) OR e.pointId = :pointId)
            """)
    Optional<Event> findOpenSimulated(@Param("ruleId") Long ruleId,
                                      @Param("deviceId") Long deviceId,
                                      @Param("pointId") Long pointId);

    /** 지금 열린 모의 이벤트 전체 — Unity 재접속 복구용. */
    List<Event> findBySimulatedTrueAndClearedAtIsNullOrderByOccurredAtDesc();

    /**
     * 모의 이벤트 확인(ack) = 종료. 실제 이벤트와 같은 규칙(acked_at + 미해소면 cleared_at 도 같은 시각). 최초 확인만 남는다.
     * {@code simulated = TRUE} 조건이 있어 실제 이벤트는 이 경로로 절대 확인되지 않는다(실제는 event 서버 웹소켓 ACK 전용).
     *
     * @return 갱신 행 수. 0 이면 이미 확인됐거나 모의 행이 아니거나 없음
     */
    // 2026-10-12: SOP 스냅샷 — event 서버 ACK_SQL 과 같은 규칙을 한 문장으로. SOP 이벤트인데 스냅샷이 없으면 0행(거절),
    // SOP 가 아니면 스냅샷을 버린다. jsonb 캐스트가 필요해 native 쿼리. 0행 사유는 서비스가 엔티티로 가린다.
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            UPDATE tbl_event
               SET acked_at   = :ackedAt,
                   acked_by   = :ackedBy,
                   cleared_at = COALESCE(cleared_at, :ackedAt),
                   sop_result = CASE WHEN sop_enabled THEN CAST(:sopResult AS jsonb) END
             WHERE event_id = :eventId AND simulated AND acked_at IS NULL
               AND (NOT sop_enabled OR CAST(:sopResult AS text) IS NOT NULL)
            """)
    int ackSimulated(@Param("eventId") UUID eventId,
                     @Param("ackedBy") Long ackedBy,
                     @Param("ackedAt") OffsetDateTime ackedAt,
                     @Param("sopResult") String sopResultJson);

    /**
     * 알람명 드롭다운("데이터 선택")용 — 실제로 발생한 적 있는 (ruleId, ruleName) 만.
     * <p>규칙 전체 목록({@code /api/event/rules})을 쓰면 한 번도 안 뜬 알람까지 나와서 화면이 지저분해진다.</p>
     */
    @Query("""
            SELECT DISTINCT e.ruleId, e.ruleName
            FROM Event e
            WHERE e.ruleId IS NOT NULL
              AND (CAST(:from AS timestamp) IS NULL OR e.occurredAt >= :from)
            ORDER BY e.ruleName
            """)
    List<Object[]> findDistinctRules(@Param("from") OffsetDateTime from);

    /** 현재 활성(미해소, clearedAt 없음) 이벤트 전체 — "지금 떠 있는 알람" 목록. {@code GET /api/event/active}. */
    List<Event> findByClearedAtIsNullOrderByOccurredAtDesc();

    /** 통계·리포트 — 시간대별 발생 건수 (KST, simulated=false). */
    @Query(value = """
            SELECT CAST(FLOOR(EXTRACT(HOUR FROM e.occurred_at AT TIME ZONE 'Asia/Seoul')) AS int) AS bucket,
                   COUNT(*) AS cnt
              FROM tbl_event e
             WHERE e.simulated = FALSE
               AND e.occurred_at >= :from
               AND e.occurred_at <= :to
             GROUP BY 1
             ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> countHourlyOccurrences(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /** 통계·리포트 — 일별 발생 건수 (KST, simulated=false). */
    @Query(value = """
            SELECT CAST((e.occurred_at AT TIME ZONE 'Asia/Seoul') AS date) AS bucket,
                   COUNT(*) AS cnt
              FROM tbl_event e
             WHERE e.simulated = FALSE
               AND e.occurred_at >= :from
               AND e.occurred_at <= :to
             GROUP BY 1
             ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> countDailyOccurrences(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /** 통계·리포트 — 월별 발생 건수 (KST, simulated=false). */
    @Query(value = """
            SELECT CAST(date_trunc('month', e.occurred_at AT TIME ZONE 'Asia/Seoul') AS date) AS bucket,
                   COUNT(*) AS cnt
              FROM tbl_event e
             WHERE e.simulated = FALSE
               AND e.occurred_at >= :from
               AND e.occurred_at <= :to
             GROUP BY 1
             ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> countMonthlyOccurrences(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    /** 통계·리포트 — 심각도(level)별 발생 건수 (simulated=false). */
    @Query(value = """
            SELECT COALESCE(NULLIF(TRIM(e.level), ''), 'UNKNOWN') AS lvl,
                   COUNT(*) AS cnt
              FROM tbl_event e
             WHERE e.simulated = FALSE
               AND e.occurred_at >= :from
               AND e.occurred_at <= :to
             GROUP BY 1
             ORDER BY COUNT(*) DESC
            """, nativeQuery = true)
    List<Object[]> countByLevel(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
