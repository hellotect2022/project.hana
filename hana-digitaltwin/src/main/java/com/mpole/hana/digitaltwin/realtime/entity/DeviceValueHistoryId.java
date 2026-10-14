package com.mpole.hana.digitaltwin.realtime.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * {@link DeviceValueHistory} 복합키. TimescaleDB 하이퍼테이블 파티션 컬럼
 * (device_id 해시 + created_at 범위) = 자연키이며, PRIMARY KEY(device_id, created_at) 와 1:1 대응한다.
 *
 * <p>단일 장비는 최소 1초 간격으로만 값이 들어오므로 (device_id, created_at) 는 충돌하지 않는다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DeviceValueHistoryId implements Serializable {

    private Long deviceId;

    private OffsetDateTime createdAt;
}
