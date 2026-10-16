package com.mpole.hana.digitaltwin.overview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

/**
 * 종합 현황 항목의 구성 대상 (2026-10-16). 여러 개면 합산한다.
 * 포인트·분류·인프라는 FK 없이 id 만 둔다(소프트 참조).
 */
@Entity
@Table(name = "tbl_overview_item_member")
@Getter
@Setter
@NoArgsConstructor
public class OverviewItemMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private OverviewItem item;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", length = 16, nullable = false)
    @Comment("POINT / CATEGORY / INFRA")
    private OverviewEnums.MemberType memberType;

    @Column(name = "point_id")
    @Comment("POINT - tbl_device_point.point_id")
    private Long pointId;

    @Column(name = "category_id")
    @Comment("CATEGORY - 소분류 category_id")
    private Long categoryId;

    @Column(name = "tag_name", length = 100)
    @Comment("CATEGORY - 합산할 태그명")
    private String tagName;

    @Column(name = "infra_id")
    @Comment("INFRA - tbl_device_infra.infra_id")
    private Long infraId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
