package com.mpole.hana.digitaltwin.location.repository;

import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnityLocFloorRepository extends JpaRepository<UnityLocFloor,Long> {

    /** 층 선택 옵션용 — 층 번호 오름차순 전체 */
    List<UnityLocFloor> findAllByOrderByFloorNumAsc();
}
