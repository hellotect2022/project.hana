package com.mpole.hana.digitaltwin.device.entity;

import com.mpole.hana.digitaltwin.device.property.DevicePropertyType;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_device_category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceCategory extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="category_id")
    private Long categoryId;

    @Column(name="category_code", length = 200)
    private String categoryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="category_parent_id")
    private DeviceCategory parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeviceCategory> children = new ArrayList<>();

    private Integer depth; // 0=대분류, 1=중분류, 2=소분류

    @Column(name="category_name",nullable = false, length = 200)
    private String categoryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", length = 32)
    private DevicePropertyType propertyType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name="display_order")
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name="is_root")
    private boolean isRoot;

    @Column(name="is_leaf")
    private boolean isLeaf;

    @JdbcTypeCode(SqlTypes.JSON)
    @Comment("카테고리별 장비 속성값 정의")
    @Column(name = "schema_definition", columnDefinition = "jsonb DEFAULT '[]'")
    @Builder.Default
    private List<PointSchemaDefinition> schemaDefinitions = new ArrayList<>();

    public boolean isSmallCategory() {
        return depth != null && depth == DeviceCategoryDepth.MAX_DEPTH;
    }
}
