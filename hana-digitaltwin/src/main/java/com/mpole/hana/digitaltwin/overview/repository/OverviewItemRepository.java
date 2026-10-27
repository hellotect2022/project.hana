package com.mpole.hana.digitaltwin.overview.repository;

import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums;
import com.mpole.hana.digitaltwin.overview.entity.OverviewItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OverviewItemRepository extends JpaRepository<OverviewItem, Long> {

    List<OverviewItem> findAllByOrderBySectionAscSortOrderAscItemIdAsc();

    List<OverviewItem> findAllBySectionOrderBySortOrderAscItemIdAsc(OverviewEnums.Section section);

    List<OverviewItem> findAllByActiveTrueOrderBySectionAscSortOrderAscItemIdAsc();

    @Query("SELECT COALESCE(MAX(i.sortOrder), 0) FROM OverviewItem i WHERE i.section = :section")
    int maxSortOrder(@Param("section") OverviewEnums.Section section);

    /** 이 카드를 참조(ITEM)하는 항목 이름 — 자립률 등이 쓰는 카드 삭제를 막을 때 */
    @Query("SELECT DISTINCT i.label FROM OverviewItem i JOIN i.members m WHERE m.refItemId = :refItemId")
    List<String> findLabelsReferencing(@Param("refItemId") Long refItemId);
}
