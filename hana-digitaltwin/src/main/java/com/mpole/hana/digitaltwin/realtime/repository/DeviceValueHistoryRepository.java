package com.mpole.hana.digitaltwin.realtime.repository;

import com.mpole.hana.digitaltwin.realtime.entity.DeviceValueHistory;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceValueHistoryId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

/**
 * 디바이스 실시간 값 이력 조회(읽기 전용). 쓰기는 event-server(R2DBC append).
 */
public interface DeviceValueHistoryRepository extends JpaRepository<DeviceValueHistory, DeviceValueHistoryId> {

    /**
     * device 별 이력을 기간(created_at from~to, 선택) 으로 페이징 조회. 정렬은 Pageable(created_at desc 권장).
     */
    @Query("""
            SELECT h FROM DeviceValueHistory h
            WHERE h.deviceId = :deviceId
              AND (CAST(:from AS timestamp) IS NULL OR h.createdAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR h.createdAt <= :to)
            """)
    Page<DeviceValueHistory> findByDeviceAndPeriod(
            @Param("deviceId") Long deviceId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );
}
