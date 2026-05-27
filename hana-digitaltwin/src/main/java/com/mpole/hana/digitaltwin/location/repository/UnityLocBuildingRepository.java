package com.mpole.hana.digitaltwin.location.repository;

import com.mpole.hana.digitaltwin.location.entity.UnityLocBuilding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnityLocBuildingRepository extends JpaRepository<UnityLocBuilding,Long> {

    @Query("""
            SELECT b FROM UnityLocBuilding b
            LEFT JOIN FETCH b.floors f
            LEFT JOIN FETCH f.zones z
            ORDER BY b.buildingId ASC, f.floorId ASC, z.zoneId ASC
            """)
    List<UnityLocBuilding> fetchAllWithFloorsAndZones();
}
