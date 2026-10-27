package com.mpole.hana.digitaltwin.overview.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16)
    @Comment("ENERGY_INDEPENDENCE 전용 - NUMERATOR(분자) / DENOMINATOR(분모). 다른 영역은 NULL")
    private OverviewEnums.Role role;

    @Column(name = "coefficient", nullable = false, precision = 18, scale = 6,
            columnDefinition = "numeric(18,6) NOT NULL DEFAULT 1")
    @Comment("ENERGY_INDEPENDENCE 전용 - 참조 카드 값에 곱할 계수 = 계수식(coefficient_expr)의 곱. 다른 영역은 1")
    private BigDecimal coefficient = BigDecimal.ONE;

    @Column(name = "ref_item_id")
    @Comment("ITEM - 참조 카드 tbl_overview_item.item_id(금일 에너지 사용·생산)")
    private Long refItemId;

    @Column(name = "coefficient_expr", length = 200)
    @Comment("ENERGY_INDEPENDENCE 전용 - 계수식(곱할 숫자를 * 로 연결, 예: 10.5*1.1). 원래 수식대로 남기는 용도")
    private String coefficientExpr;
}
