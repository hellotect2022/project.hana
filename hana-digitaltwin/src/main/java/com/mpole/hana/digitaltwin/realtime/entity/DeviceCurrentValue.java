package com.mpole.hana.digitaltwin.realtime.entity;

import com.mpole.hana.common.constant.ConnectionState;
import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "tbl_device_current_value") // 구 tbl_device_snapshot_last (points: 맵 → 배열)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceCurrentValue extends DateEntity {
    @Id
    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "device_key")
    private String deviceKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "points", columnDefinition = "jsonb")
    private List<XnPointData> points;

    /**
     * 통신상태(설계 v2.1 — 상태는 상태 테이블에).
     * 쓰기: 인프라 소속 장비=event reconcile(2차), 미소속(MQTT 등)=digitaltwin 타깃 upsert.
     * SnapshotService 의 값 upsert 는 이 컬럼을 건드리지 않는다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_state", length = 32)
    private ConnectionState connectionState;
}
