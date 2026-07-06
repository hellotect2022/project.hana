package com.mpole.hana.digitaltwin.location.repository;

import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnityLocZoneRepository extends JpaRepository<UnityLocZone,Long> {

    /** mesh_name 중복 검사 (등록 시) */
    boolean existsByZoneMeshName(String zoneMeshName);

    /** mesh_name 중복 검사 (수정 시 자기 자신 제외) */
    boolean existsByZoneMeshNameAndZoneIdNot(String zoneMeshName, Long zoneId);

    /** 전체 목록 — 층 정보 함께 로딩 */
    @Query("select z from UnityLocZone z join fetch z.floor f order by f.floorNum, z.zoneMeshName")
    List<UnityLocZone> findAllWithFloor();

    /** 층별 목록 — 층 정보 함께 로딩 */
    @Query("select z from UnityLocZone z join fetch z.floor f where f.floorId = :floorId order by z.zoneMeshName")
    List<UnityLocZone> findByFloorIdWithFloor(@Param("floorId") Long floorId);
}
