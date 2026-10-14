package com.mpole.hana.digitaltwin.device.entity;

import com.mpole.hana.common.constant.ConnectionState;
import com.mpole.hana.digitaltwin.device.property.DevicePropertyInfo;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Id;

@Entity
@Table(name = "tbl_device",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_key", columnNames = {"device_key"}),
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "device_key")
    private String deviceKey;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "device_display_name")
    @Comment("장비 UI 표기명 — 비면 device_name 으로 폴백(프론트)")
    private String deviceDisplayName;

    private Boolean active;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DeviceCategory category;

//    /**
//     * @deprecated 3D 에셋은 카테고리(DeviceCategory.defaultAsset) 기준으로 렌더한다.
//     * 컬럼/필드는 단계적 전환을 위해 남겨두되 사용하지 않는다(후속 drop 예정).
//     */
//    @Deprecated
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "asset_id")
//    @Comment("(deprecated) 디바이스 단위 3D 에셋 — 카테고리 defaultAsset 로 대체됨")
//    private UnityAsset unityAsset;

    @OneToOne(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private DevicePlacement placement;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            referencedColumnName = "device_id",
            insertable = false,
            updatable = false
    )
    private DeviceCurrentValue deviceCurrentValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Comment("장치 고유 속성")
    @Column(name = "property_info", columnDefinition = "jsonb")
    private DevicePropertyInfo propertyInfo;

    @Column(name = "alarm_state", nullable = false)
    @Comment("알람 마스터 스위치 — false 면 이 장비 알람을 client 로 발행하지 않음")
    @Builder.Default
    private boolean alarmState = true;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "operation_state", length = 32)
//    @Comment("동작상태(산정 로직 추후)")
//    @Builder.Default
//    private OperationState operationState = OperationState.UNKNOWN;
//
//    /**
//     * @deprecated v2.1 설계 — 통신상태는 tbl_device_current_value.connection_state 로 이관(상태는 상태 테이블에).
//     * 판독/쓰기 소스에서 제외됨. 컬럼은 정리 SQL 전까지 유지.
//     */
//    @Deprecated
//    @Enumerated(EnumType.STRING)
//    @Column(name = "connection_state", length = 32)
//    @Comment("(폐기 예정) 연결상태 — current_value.connection_state 로 이관됨")
//    @Builder.Default
//    private ConnectionState connectionState = ConnectionState.UNKNOWN;

    @Column(name = "infra_id")
    @Comment("소속 인프라(tbl_device_infra FK, ON DELETE SET NULL). NULL=상속 비대상(MQTT 등 수동 관리)")
    private Long infraId;

    public void clearPlacement() {
        this.placement = null;
    }

}
