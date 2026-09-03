package com.mpole.hana.digitaltwin.realtime.repository;

import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * tbl_device_current_value 접근(디지털트윈 측).
 * <p>값(points)의 쓰기 주체는 hana-event(SnapshotService)다. 여기서는 v2.1 설계에 따라
 * <b>connection_state 컬럼만</b> 타깃 갱신한다 — 엔티티 save 금지(로드 스냅샷의 points 를
 * 통째로 되써서 1초 값 파이프라인과 경합함).</p>
 */
public interface DeviceCurrentValueRepository extends JpaRepository<DeviceCurrentValue, Long> {

    /**
     * 수동 통신상태 반영(infra 미소속 장비 전용 — MQTT 등). 행이 없으면 생성(points NULL 허용).
     * <p>updated_at 은 값 신선도(staleness) 의미라 기존 행에서는 건드리지 않는다.</p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO tbl_device_current_value (device_id, device_key, connection_state, created_at, updated_at)
            VALUES (:deviceId, :deviceKey, :state, NOW(), NOW())
            ON CONFLICT (device_id)
            DO UPDATE SET connection_state = :state
            """, nativeQuery = true)
    int upsertConnectionState(
            @Param("deviceId") Long deviceId,
            @Param("deviceKey") String deviceKey,
            @Param("state") String state
    );
}
