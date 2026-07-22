package com.mpole.hana.digitaltwin.systemMenu.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tbl_device_sub_system", indexes = {
        @Index(name = "idx_sub_system_system", columnList = "system_id"),
        @Index(name = "idx_sub_system_sort", columnList = "sort_order")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DeviceSubSystem extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sub_system_id")
    private Long subSystemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_sub_system_system"))
    @Comment("상위 BMS 시스템")
    private DeviceSystem deviceSystem;

    @Column(name = "sub_system_name", nullable = false)
    @Comment("하위 시스템(패널 그룹)명")
    private String subSystemName;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    @Comment("정렬 순서")
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    @Comment("활성화 여부")
    private Boolean active = true;

    // Set 사용: subSystems(List/bag) 와 함께 JOIN FETCH 시 MultipleBagFetchException 회피(bag 1 + set 1).
    @OneToMany(mappedBy = "deviceSubSystem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<DeviceSystemCategory> categoryMappings = new LinkedHashSet<>();

    public static DeviceSubSystem create(DeviceSystem deviceSystem, String subSystemName,
                                         Integer sortOrder, Boolean active) {
        return DeviceSubSystem.builder()
                .deviceSystem(deviceSystem)
                .subSystemName(subSystemName)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .active(active != null ? active : true)
                .build();
    }

    public void update(String subSystemName, Integer sortOrder, Boolean active) {
        if (subSystemName != null) {
            this.subSystemName = subSystemName;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        if (active != null) {
            this.active = active;
        }
    }

    public void changeSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void addCategoryMapping(DeviceSystemCategory mapping) {
        this.categoryMappings.add(mapping);
    }

    public void clearCategoryMappings() {
        this.categoryMappings.clear();
    }
}
