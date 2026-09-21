package com.mpole.hana.digitaltwin.location.repository;

import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnityLocFloorRepository extends JpaRepository<UnityLocFloor,Long> {

    /**
     * 층 선택 옵션용 — <b>최하층부터</b> 전체.
     *
     * <p>{@code floorNum} 이 아니라 {@code sortOrder} 로 정렬한다 — 옥외는 층수가 없어
     * {@code floor_num = 100} 이라, 층 번호로 정렬하면 PH2F 뒤로 밀린다.</p>
     */
    List<UnityLocFloor> findAllByOrderBySortOrderAsc();

    // [보존] 층 번호 정렬 — 옥외가 맨 뒤로 밀려 sortOrder 기준으로 교체했다.
    // List<UnityLocFloor> findAllByOrderByFloorNumAsc();
}
