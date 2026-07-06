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

    @Query("""
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.placement p
            LEFT JOIN FETCH p.unityZone z
            LEFT JOIN FETCH z.floor f
            LEFT JOIN FETCH d.deviceCurrentValue
            WHERE d.category.categoryId IN :categoryIds
              AND z.zoneId = :zoneId
            ORDER BY d.deviceId ASC
            """)
    List<Device> fetchForLightingByZone(@Param("categoryIds") Collection<Long> categoryIds,
                                        @Param("zoneId") Long zoneId);
}
