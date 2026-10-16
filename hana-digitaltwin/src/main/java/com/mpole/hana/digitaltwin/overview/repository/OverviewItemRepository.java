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
}
