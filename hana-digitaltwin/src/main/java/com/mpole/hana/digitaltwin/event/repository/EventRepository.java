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
import java.util.UUID;

/**
 * 이벤트 정본 조회. 쓰기(발생/해소)는 event-server(R2DBC)가 담당하고,
 * digitaltwin 은 조회와 <b>확인(ack)</b> 만 수행한다.
 */
public interface EventRepository extends JpaRepository<Event, UUID> {

    /**
     * 기간(occurredAt) / level / deviceId / 활성여부 선택 필터 조회.
     * 각 파라미터는 null 이면 조건에서 제외한다. 정렬은 Pageable(예: occurredAt desc).
     */
    @Query("""
            SELECT e FROM Event e
            WHERE (CAST(:level AS string) IS NULL OR e.level = :level)
              AND (CAST(:deviceId AS long) IS NULL OR e.deviceId = :deviceId)
              AND (CAST(:active AS boolean) IS NULL OR e.active = :active)
              AND (CAST(:from AS timestamp) IS NULL OR e.occurredAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR e.occurredAt <= :to)
            """)
    Page<Event> findByOptionalFilters(
            @Param("level") String level,
            @Param("deviceId") Long deviceId,
            @Param("active") Boolean active,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );

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
     * @param completed   true=조치완료(ackedAt 존재), false=미조치, null=전체
     */
    @Query("""
            SELECT new com.mpole.hana.digitaltwin.event.dto.AlarmHistoryRow(
                       e.eventId, e.ruleId, e.ruleName, e.level,
                       e.deviceId, d.deviceName, e.deviceKey,
                       f.floorName, z.zoneName,
                       c.categoryId, c.categoryName,
                       e.occurredAt, e.ackedAt, e.active)
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
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );

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

    /** 현재 활성 이벤트 전체 — "지금 떠 있는 알람" 목록. Active 축 도입으로 비로소 가능해진 조회. */
    List<Event> findByActiveTrueOrderByOccurredAtDesc();

    /** 미확인(active 무관) — 놓친 알람 확인용. */
    List<Event> findByAckedAtIsNullOrderByOccurredAtDesc();

    /**
     * 확인(ack) 처리 — 이미 확인된 건은 갱신하지 않는다(최초 확인자·시각 보존).
     *
     * @return 갱신된 행 수(0 이면 이미 확인됨 또는 미존재)
     */
    @Modifying
    @Query("""
            UPDATE Event e
               SET e.ackedAt = :ackedAt, e.ackedBy = :ackedBy, e.updatedAt = :ackedAt
             WHERE e.eventId = :eventId AND e.ackedAt IS NULL
            """)
    int ack(@Param("eventId") UUID eventId,
            @Param("ackedBy") Long ackedBy,
            @Param("ackedAt") OffsetDateTime ackedAt);
}
