package com.mpole.hana.digitaltwin.device.entity;

import com.mpole.hana.digitaltwin.device.property.DevicePropertyInfo;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
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

    private Boolean active;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private DeviceCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    @Comment("소분류(depth=2) 기본 3D 에셋")
    private UnityAsset unityAsset;

    @OneToOne(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private DevicePlacement placement;

    @JdbcTypeCode(SqlTypes.JSON)
    @Comment("장치 고유 속성")
    @Column(name = "property_info", columnDefinition = "jsonb")
    private DevicePropertyInfo propertyInfo;

    public void clearPlacement() {
        this.placement = null;
    }

}
