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

    /** zone_code 중복 검사 (등록 시). code 는 null 허용이라 값이 있을 때만 호출한다. */
    boolean existsByZoneCode(String zoneCode);

    /** 외부 연동 식별자로 구역 조회 — HQS stationId/wcId 등이 이 값으로 들어온다. */
    java.util.Optional<UnityLocZone> findByZoneCode(String zoneCode);

    /** zone_code 중복 검사 (수정 시 자기 자신 제외) */
    boolean existsByZoneCodeAndZoneIdNot(String zoneCode, Long zoneId);

    /** 하위 구역 존재 여부 — 삭제 가능 판단용 */
    boolean existsByParent_ZoneId(Long parentZoneId);

    /**
     * 트리 해석 전용 경량 조회 — {@code [zone_id, zone_parent_id]} 만 읽는다.
     * <p>{@link com.mpole.hana.digitaltwin.location.service.ZoneCascadeService} 가
     * 서브트리를 구할 때 전체를 한 번 읽는데, 엔티티를 통째로 로딩할 이유가 없다.</p>
     */
    @Query("select z.zoneId, z.parent.zoneId from UnityLocZone z")
    List<Object[]> findAllIdAndParentId();

    /** 특정 구역의 직속 자식 — 층 정보 포함 */
    @Query("select z from UnityLocZone z join fetch z.floor f where z.parent.zoneId = :parentId order by z.zoneName")
    List<UnityLocZone> findByParentIdWithFloor(@Param("parentId") Long parentId);
}
