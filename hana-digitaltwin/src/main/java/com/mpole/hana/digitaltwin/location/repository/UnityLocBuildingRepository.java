package com.mpole.hana.digitaltwin.location.repository;

import com.mpole.hana.digitaltwin.location.entity.UnityLocBuilding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnityLocBuildingRepository extends JpaRepository<UnityLocBuilding,Long> {

//    @Query("""
//            SELECT b FROM UnityLocBuilding b
//            LEFT JOIN FETCH b.floors f
//            LEFT JOIN FETCH f.zones z
//            ORDER BY b.buildingId ASC, f.floorId ASC, z.zoneId ASC
//            """)
    /**
     * 건물 + 층 목록. 층은 <b>최하층부터</b> 내려간다.
     *
     * <p>예전에는 {@code f.floorId ASC} 로 정렬했는데, floor_id 는 등록 순서라
     * {@code 1F=1 · B1F=18 · 옥외=100} 이었다. 그래서 응답이
     * <b>지상 → 지하 → 옥외</b> 순으로 뒤섞여 나갔다. {@code f.sortOrder} 로 바꾼다
     * ({@code floorNum} 을 쓰면 층수가 없는 옥외가 맨 뒤로 밀린다).</p>
     */
    @Query("""
        SELECT b FROM UnityLocBuilding b
        LEFT JOIN FETCH b.floors f
        ORDER BY b.buildingId ASC, f.sortOrder ASC
        """)
    List<UnityLocBuilding> fetchAllWithFloorsAndZones();
}
