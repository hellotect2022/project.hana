package com.mpole.hana.digitaltwin.systemMenu.entity;

import com.mpole.hana.digitaltwin.systemMenu.dto.DeviceSystemCreateRequest;
import com.mpole.hana.digitaltwin.systemMenu.dto.DeviceSystemUpdateRequest;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_device_system", indexes = {
        @Index(name = "idx_device_system_sort_order", columnList = "sort_order"),
        @Index(name = "idx_device_system_active", columnList = "active")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DeviceSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "system_id")
    private Long systemId;

    @Column(name = "system_name", nullable = false)
    @Comment("BMS 시스템명")
    private String systemName;

    @Column(name = "system_code", nullable = false, unique = true)
    @Comment("BMS 시스템 코드")
    private String systemCode;

    @Column(name = "sort_order", nullable = false)
    @Comment("정렬 순서")
    private Integer sortOrder;

    @Column(nullable = false)
    @Builder.Default
    @Comment("활성화 여부")
    private Boolean active = true;

    @OneToMany(mappedBy = "deviceSystem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeviceSystemCategory> categoryMappings = new ArrayList<>();

    @OneToMany(mappedBy = "deviceSystem")
    @Builder.Default
    private List<RoleDeviceSystem> roleDeviceSystems = new ArrayList<>();

    public static DeviceSystem create(DeviceSystemCreateRequest request) {
        return DeviceSystem.builder()
                .systemName(request.systemName())
                .systemCode(request.systemCode())
                .sortOrder(request.sortOrder())
                .active(true)
                .build();
    }

    public void update(DeviceSystemUpdateRequest request) {
        this.systemName = request.systemName();
        this.systemCode = request.systemCode();
        if (request.active() != null) {
            this.active = request.active();
        }
    }

    public void changeSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void replaceCategoryMappings(List<DeviceSystemCategory> mappings) {
        this.categoryMappings.clear();
        this.categoryMappings.addAll(mappings);
    }
}
