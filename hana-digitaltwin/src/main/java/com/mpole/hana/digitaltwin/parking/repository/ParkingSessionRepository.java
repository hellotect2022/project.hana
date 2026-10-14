package com.mpole.hana.digitaltwin.parking.repository;

import com.mpole.hana.digitaltwin.parking.entity.ParkingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

/**
 * 주차 건 조회 (2026-10-13). 기간 필터는 "그 기간에 입차 <b>또는</b> 출차가 있었던 건" 이다.
 * 선택 필터의 {@code CAST(... ) IS NULL} 은 파라미터 타입 추론 실패를 막는 기존 패턴(이력 쿼리와 같다).
 */
public interface ParkingSessionRepository extends JpaRepository<ParkingSession, String> {

    String FILTER = """
            WHERE ( (s.inAt IS NOT NULL
                     AND (CAST(:from AS timestamp) IS NULL OR s.inAt >= :from)
                     AND (CAST(:to   AS timestamp) IS NULL OR s.inAt <  :to))
                 OR (s.outAt IS NOT NULL
                     AND (CAST(:from AS timestamp) IS NULL OR s.outAt >= :from)
                     AND (CAST(:to   AS timestamp) IS NULL OR s.outAt <  :to)) )
              AND (CAST(:carLike AS string) IS NULL OR s.carNo LIKE :carLike)
              AND (CAST(:status  AS string) IS NULL OR s.status = :status)
            """;

    /** 목록 — 가장 최근 움직임(출차 있으면 출차, 없으면 입차) 순 */
    @Query(value = "SELECT s FROM ParkingSession s " + FILTER + " ORDER BY COALESCE(s.outAt, s.inAt) DESC",
            countQuery = "SELECT count(s) FROM ParkingSession s " + FILTER)
    Page<ParkingSession> search(@Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                @Param("carLike") String carLike,
                                @Param("status") String status,
                                Pageable pageable);

    /** 기간 안 입차 수 */
    @Query("""
            SELECT count(s) FROM ParkingSession s
             WHERE s.inAt IS NOT NULL
               AND (CAST(:from AS timestamp) IS NULL OR s.inAt >= :from)
               AND (CAST(:to   AS timestamp) IS NULL OR s.inAt <  :to)
            """)
    long countIn(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** 기간 안 출차 수 */
    @Query("""
            SELECT count(s) FROM ParkingSession s
             WHERE s.outAt IS NOT NULL
               AND (CAST(:from AS timestamp) IS NULL OR s.outAt >= :from)
               AND (CAST(:to   AS timestamp) IS NULL OR s.outAt <  :to)
            """)
    long countOut(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** 지금 주차중 — 기간과 무관하게 입차 기록이 있고 아직 안 나간 건 */
    @Query("SELECT count(s) FROM ParkingSession s WHERE s.hasIn = TRUE AND s.status = 'PARKED'")
    long countParked();
}
