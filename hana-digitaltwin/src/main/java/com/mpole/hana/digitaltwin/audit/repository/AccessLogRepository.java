package com.mpole.hana.digitaltwin.audit.repository;

import com.mpole.hana.digitaltwin.audit.entity.AccessLog;
import com.mpole.hana.digitaltwin.audit.entity.AccessLogType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    Page<AccessLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            OffsetDateTime fromInclusive,
            OffsetDateTime toInclusive,
            Pageable pageable
    );
    long countByLogTypeAndCreatedAtBetween(
            AccessLogType logType,
            OffsetDateTime fromInclusive,
            OffsetDateTime toInclusive
    );

    @Query(value = """
            SELECT CAST(created_at AS date), COUNT(*)
            FROM tbl_log_access
            WHERE log_type = :type
              AND created_at >= :from AND created_at <= :to
            GROUP BY CAST(created_at AS date)
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> countByDayAndType(
            @Param("type") String type,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM AccessLog a WHERE a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
