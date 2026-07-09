package com.mpole.hana.digitaltwin.device.entity;

import com.mpole.hana.digitaltwin.device.property.DevicePropertyType;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_device_category"
        //uniqueConstraints = {@UniqueConstraint(name = "uk_category_code_depth", columnNames = {"category_code", "depth"})}
)
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

    @Column(name = "category_name_en", length = 200)
    @Comment("카테고리 영문 표시명 (선택)")
    private String categoryNameEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", foreignKey = @ForeignKey(name = "fk_device_category_asset"))
    @Comment("카테고리 기본 3D 에셋(소속 디바이스 렌더 기준)")
    private UnityAsset defaultAsset;

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
