package com.mpole.hana.digitaltwin.audit.repository;

import com.mpole.hana.digitaltwin.audit.entity.UsageLog;
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
public interface UsageLogRepository extends JpaRepository<UsageLog,Long> {

    Page<UsageLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            OffsetDateTime fromInclusive,
            OffsetDateTime toInclusive,
            Pageable pageable
    );

    long countByCreatedAtBetween(OffsetDateTime fromInclusive, OffsetDateTime toInclusive);

    @Query(value = """
            SELECT menu_label, COUNT(*)
            FROM tbl_log_usage
            WHERE created_at >= :from AND created_at <= :to
            GROUP BY menu_label
            ORDER BY COUNT(*) DESC
            """, nativeQuery = true)
    List<Object[]> countTopMenus(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );

    @Query(value = """
            SELECT CAST(created_at AS date), COUNT(*)
            FROM tbl_log_usage
            WHERE created_at >= :from AND created_at <= :to
            GROUP BY CAST(created_at AS date)
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> countUsageByDay(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query(value = """
            SELECT CAST(EXTRACT(HOUR FROM created_at) AS INTEGER), COUNT(*)
            FROM tbl_log_usage
            WHERE created_at >= :from AND created_at <= :to
            GROUP BY EXTRACT(HOUR FROM created_at)
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> countUsageByHour(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM UsageLog u WHERE u.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
