package com.mpole.hana.digitaltwin.overview.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 종합 현황 항목 — 클라이언트 패널의 카드 한 칸 (2026-10-16).
 * 스키마 정본은 scripts/overview/2026-10-16-overview.sql (CHECK 제약은 ddl-auto 로 생기지 않는다).
 */
@Entity
@Table(name = "tbl_overview_item")
@Getter
@Setter
@NoArgsConstructor
public class OverviewItem extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", length = 32, nullable = false)
    @Comment("ENERGY_USAGE / ENERGY_PRODUCTION / INFRA_STATUS")
    private OverviewEnums.Section section;

    @Column(name = "label", length = 100, nullable = false)
    @Comment("화면 표시 이름")
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_source", length = 32)
    @Comment("CURRENT_VALUE / SNAPSHOT_DELTA. INFRA_STATUS 는 NULL")
    private OverviewEnums.ValueSource valueSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", length = 16)
    @Comment("SNAPSHOT_DELTA 기간 시작: TODAY / MONTH / YEAR")
    private OverviewEnums.Period period;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "scale", nullable = false, precision = 18, scale = 6)
    @Comment("표시 배율(값 x scale)")
    private BigDecimal scale = BigDecimal.ONE;

    @Column(name = "decimals", nullable = false, columnDefinition = "smallint")
    private Integer decimals = 1;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, memberId ASC")
    private List<OverviewItemMember> members = new ArrayList<>();

    /** 구성 대상을 통째로 교체한다(순서 = 목록 순서). */
    public void replaceMembers(List<OverviewItemMember> next) {
        members.clear();
        for (int i = 0; i < next.size(); i++) {
            OverviewItemMember mem = next.get(i);
            mem.setItem(this);
            mem.setSortOrder(i + 1);
            members.add(mem);
        }
    }
}
