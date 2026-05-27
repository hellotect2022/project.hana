package com.mpole.hana.digitaltwin.device.repository;

import com.mpole.hana.digitaltwin.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceRepository extends JpaRepository<Device,Long> {

    @Query("""
        SELECT d FROM Device d
        LEFT JOIN FETCH d.placement p
        LEFT JOIN FETCH p.unityZone z
        LEFT JOIN FETCH z.floor f
        LEFT JOIN FETCH f.building b
        LEFT JOIN FETCH d.category c
        LEFT JOIN FETCH d.unityAsset
        WHERE d.deviceId = :deviceId
        """)
    Device fetchDeviceById(@Param("deviceId") Long deviceId);

    @Query("""
        SELECT d FROM Device d
        LEFT JOIN FETCH d.placement p
        LEFT JOIN FETCH p.unityZone z
        LEFT JOIN FETCH d.category
        ORDER BY d.deviceId ASC
        """)
    List<Device> fetchAllDevices();

    @Query("""
        SELECT d FROM Device d
        LEFT JOIN FETCH d.placement p
        LEFT JOIN FETCH p.unityZone z
        WHERE (:floorId IS NULL OR z.floor.floorId = :floorId)
          AND (:zoneId IS NULL OR z.zoneId = :zoneId)
        ORDER BY d.deviceId ASC
        """)
    List<Device> fetchSearchDevices(@Param("floorId") Long floorId, @Param("zoneId") Long zoneId);

    @Query("""
            SELECT d 
            FROM Device d
            LEFT JOIN FETCH d.placement p
            LEFT JOIN FETCH p.unityZone z
            LEFT JOIN FETCH d.category c
            LEFT JOIN FETCH d.unityAsset
            WHERE c.categoryId = :categoryId
            ORDER BY d.deviceId ASC
            """)
    List<Device> fetchTargetCategoryDevices(@Param("categoryId") Long categoryId);

    long countByCategory_CategoryId(Long categoryId);

    long countByUnityAsset_AssetId(Long assetId);

    boolean existsByDeviceKey(String deviceKey);

//    @Query("""
//            SELECT d FROM Device d
//            LEFT JOIN FETCH d.devicePoints
//            """)
//    List<Device> fetchDeviceWithPoints();
}
