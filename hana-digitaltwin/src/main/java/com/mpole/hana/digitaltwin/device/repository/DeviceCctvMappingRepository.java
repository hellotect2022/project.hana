package com.mpole.hana.digitaltwin.device.repository;

import com.mpole.hana.digitaltwin.device.dto.CctvInfoProjection;
import com.mpole.hana.digitaltwin.device.entity.DeviceCctvMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DeviceCctvMappingRepository extends JpaRepository<DeviceCctvMapping,Long> {
    @Query("""
            SELECT
               cctv.deviceId AS deviceId,
               cctv.deviceName AS deviceName,
               cctv.propertyInfo AS deviceProperty,
               f.floorId AS floorId,
               f.floorName AS floorName
            FROM DeviceCctvMapping m
            JOIN m.cctv cctv
            LEFT JOIN cctv.placement p
            LEFT JOIN p.unityZone z
            LEFT JOIN z.floor f
            WHERE m.device.deviceId = :deviceId
            """)
    List<CctvInfoProjection> findByDeviceId(@Param("deviceId") Long deviceId);

    @Modifying
    @Query("DELETE FROM DeviceCctvMapping m WHERE m.device.deviceId = :deviceId")
    void deleteByDeviceId(@Param("deviceId") Long deviceId);

    @Modifying
    @Query("DELETE FROM DeviceCctvMapping m WHERE m.device.deviceId IN :deviceIds")
    void deleteByDeviceIdIn(@Param("deviceIds") Collection<Long> deviceIds);

    @Modifying
    @Query(value = """
            INSERT INTO tbl_device_cctv_mapping (device_id, cctv_id)
            SELECT :deviceId, device_id FROM tbl_device WHERE device_id IN :cctvIds
            """, nativeQuery = true)
    void bulkInsertMappings(@Param("deviceId") Long deviceId, @Param("cctvIds") List<Long> cctvIds);
}
