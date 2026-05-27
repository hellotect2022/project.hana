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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", nullable = false)
    private DeviceSystem deviceSystem;

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
}
