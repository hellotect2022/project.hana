package com.mpole.hana.digitaltwin.buildingLighting.repository;

import com.mpole.hana.digitaltwin.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 건물조명 집계/제어용 장비 조회. 배치(층/구역)와 실시간 현재값을 함께 fetch 한다.
 * <p>{@link Device} 엔티티를 공유하지만, 조명 도메인에 필요한 fetch-join 만 별도로 둔다.
 */
@Repository
public interface LightingDeviceRepository extends JpaRepository<Device, Long> {

    @Query("""
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.placement p
            LEFT JOIN FETCH p.unityZone z
            LEFT JOIN FETCH z.floor f
            LEFT JOIN FETCH d.deviceCurrentValue
            WHERE d.category.categoryId IN :categoryIds
            ORDER BY d.deviceId ASC
            """)
    List<Device> fetchForLighting(@Param("categoryIds") Collection<Long> categoryIds);

    @Query("""
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.placement p
            LEFT JOIN FETCH p.unityZone z
            LEFT JOIN FETCH z.floor f
            LEFT JOIN FETCH d.deviceCurrentValue
            WHERE d.category.categoryId IN :categoryIds
              AND f.floorId = :floorId
            ORDER BY d.deviceId ASC
            """)
    List<Device> fetchForLightingByFloor(@Param("categoryIds") Collection<Long> categoryIds,
                                         @Param("floorId") Long floorId);

    /**
     * 구역 기준 조회.
     *
     * <p><b>단일 zoneId 가 아니라 목록을 받는다</b> — 구역에 계층이 생겨서(화장실 &gt; 부스)
     * 화장실 구역을 고르면 그 안 부스에 배치된 조명도 나와야 하기 때문이다.
     * 호출부가 {@code ZoneCascadeService.resolveSubtreeZoneIds()} 로 서브트리를 펼쳐 넘긴다.</p>
     *
     * @param zoneIds 자기 자신을 포함한 서브트리 구역 id. <b>빈 목록을 넘기지 말 것</b>(SQL 오류)
     */
    @Query("""
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.placement p
            LEFT JOIN FETCH p.unityZone z
            LEFT JOIN FETCH z.floor f
            LEFT JOIN FETCH d.deviceCurrentValue
            WHERE d.category.categoryId IN :categoryIds
              AND z.zoneId IN :zoneIds
            ORDER BY d.deviceId ASC
            """)
    List<Device> fetchForLightingByZoneIds(@Param("categoryIds") Collection<Long> categoryIds,
                                           @Param("zoneIds") Collection<Long> zoneIds);
}
