package com.mpole.hana.digitaltwin.alarm.repository;

import com.mpole.hana.digitaltwin.alarm.entity.AlarmHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface AlarmHistoryRepository extends JpaRepository<AlarmHistory, Long> {

    /**
     * 기간(occurredAt) / level / deviceId 선택 필터 조회. 각 파라미터는 null 이면 조건에서 제외한다.
     * 정렬은 Pageable(예: occurredAt desc) 에 따른다.
     */
    @Query("""
            SELECT h FROM AlarmHistory h
            WHERE (:level IS NULL OR h.level = :level)
              AND (:deviceId IS NULL OR h.deviceId = :deviceId)
              AND (:from IS NULL OR h.occurredAt >= :from)
              AND (:to IS NULL OR h.occurredAt <= :to)
            """)
    Page<AlarmHistory> findByOptionalFilters(
            @Param("level") String level,
            @Param("deviceId") Long deviceId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );
}
