package com.mpole.hana.digitaltwin.infra.entity;

import com.mpole.hana.common.constant.ConnectionState;
import com.mpole.hana.common.constant.InfraType;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 인프라 마스터(중계기/NVR/VMS/미들웨어) — 장비 통신상태 상속의 소스.
 * <p>스키마는 scripts/infra/2026-08-27-device-infra.sql 수동 마이그레이션이 정본
 * (UNIQUE/DEFAULT/FK 는 ddl-auto 로 소급 생성되지 않음).</p>
 * <p>network_state 는 (2차) event 서버가 vw_02 스냅샷으로 갱신하며,
 * 장비 상속은 tbl_device.infra_id(FK) + device_id 경유 reconcile 로 수행된다.</p>
 */
@Entity
@Table(name = "tbl_device_infra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceInfra extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "infra_id")
    private Long infraId;

    @Column(name = "ref_infra_code", length = 100, unique = true)
    @Comment("외부 매핑 키(vw_02 식별 컬럼 값, soft ref). 수동 타입(NVR 등)은 NULL 허용")
    private String refInfraCode;

    @Column(name = "infra_name", length = 200, nullable = false)
    private String infraName;

    @Column(name = "infra_display_name", length = 200)
    @Comment("표시 이름(화면용). 없으면 infra_name 을 쓴다")
    private String infraDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "infra_type", length = 32, nullable = false)
    private InfraType infraType;

    @Enumerated(EnumType.STRING)
    @Column(name = "network_state", length = 32, nullable = false)
    @Comment("네트워크 상태 — 장비 connection_state 상속의 소스")
    @Builder.Default
    private ConnectionState networkState = ConnectionState.UNKNOWN;

    @Column(name = "last_comm_at")
    @Comment("마지막 스냅샷 수신 시각(전이 시각 아님)")
    private OffsetDateTime lastCommAt;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    @Comment("타입별 속성(뷰 원본 보존 포함)")
    private Map<String, Object> properties;
}
