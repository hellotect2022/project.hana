package com.mpole.hana.digitaltwin.realtime.entity;

import com.mpole.hana.common.dto.XnPointData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 디바이스 실시간 값 이력(append-only). device 별 bundle 1행을 계속 쌓는다.
 *
 * <p>PostgreSQL + TimescaleDB 하이퍼테이블(파티션: created_at 범위 + device_id 해시)이며,
 * 실제 테이블/하이퍼테이블/보존정책 생성은 DB 마이그레이션이 담당한다(ddl-auto 아님).
 * 쓰기는 event-server(R2DBC append)가, 읽기(이력 조회)는 digitaltwin(JPA)이 담당한다.</p>
 *
 * <p>surrogate id 없이 <b>복합 PK (device_id, created_at)</b> = 하이퍼테이블 파티션 컬럼과 정확히 일치.
 * 단일 장비는 최소 1초 간격 수집이라 (device_id, created_at) 충돌 없음.</p>
 */
@Entity
@Table(name = "tbl_device_value_history")
@IdClass(DeviceValueHistoryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceValueHistory {

    @Id
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Id
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "device_key")
    private String deviceKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "points", columnDefinition = "jsonb")
    private List<XnPointData> points;
}
