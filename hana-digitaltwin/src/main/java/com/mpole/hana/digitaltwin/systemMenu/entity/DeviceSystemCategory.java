package com.mpole.hana.digitaltwin.systemMenu.entity;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "tbl_device_system_category", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_system_category", columnNames = {"system_id", "category_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DeviceSystemCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long mappingId;

    // 레거시(비파괴): system_id 는 NOT NULL 유지. UNIQUE(system_id, category_id) 도 그대로.
    // 3단 전환 후에도 subSystem 을 통해 상위 시스템을 함께 세팅하여 NOT NULL 을 충족한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", nullable = false)
    private DeviceSystem deviceSystem;

    // 신규: subSystem 기준 매핑. 기존 행에는 값이 없으므로 nullable 유지(마이그레이션 안전).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_system_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_device_system_category_sub_system"))
    @Comment("하위 시스템(subSystem)")
    private DeviceSubSystem deviceSubSystem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @Comment("소분류 카테고리")
    private DeviceCategory category;

    public static DeviceSystemCategory of(DeviceSystem deviceSystem, DeviceCategory category) {
        return DeviceSystemCategory.builder()
                .deviceSystem(deviceSystem)
                .category(category)
                .build();
    }

    /**
     * subSystem 기준 매핑 팩토리. subSystem 을 세팅하면서 상위 시스템(system_id NOT NULL)도
     * subSystem.getDeviceSystem() 으로 함께 세팅하여 정합성과 제약을 동시에 충족한다.
     */
    public static DeviceSystemCategory of(DeviceSubSystem deviceSubSystem, DeviceCategory category) {
        return DeviceSystemCategory.builder()
                .deviceSubSystem(deviceSubSystem)
                .deviceSystem(deviceSubSystem.getDeviceSystem())
                .category(category)
                .build();
    }
}
