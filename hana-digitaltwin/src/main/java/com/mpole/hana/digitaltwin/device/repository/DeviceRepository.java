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

    @Query("""
        SELECT d FROM Device d
        WHERE d.deviceKey LIKE 'HVAC%'
           OR d.deviceKey LIKE 'HSE%'
           OR d.deviceKey LIKE 'ELEC_TE_%'
           OR d.deviceKey LIKE 'PS_%'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchMechanicalSituationDevices();

    @Query("""
        SELECT d FROM Device d
        LEFT JOIN d.category c
        WHERE d.deviceKey LIKE 'RM_KWH%'
           OR c.categoryCode = 'KWH'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchElectricMeterDevices();

    @Query("""
        SELECT d FROM Device d
        WHERE d.deviceKey LIKE 'RE_PV%'
           OR d.deviceKey LIKE 'RE_GS%'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchRenewableDevices();

    @Query("""
        SELECT d FROM Device d
        WHERE d.deviceKey LIKE 'HSE_CLG_CHT%'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchIndoorEnvDevices();

    @Query("""
        SELECT d FROM Device d
        WHERE d.deviceKey LIKE 'PS_PRD_SHV%'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchMainIncomerDevices();

    @Query("""
        SELECT d FROM Device d
        WHERE d.deviceKey LIKE 'RM_GM%'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchGasMeterDevices();

    /** 전력 구간 — 특고압 탭(한전 인입 SHV + 고압반 HV). */
    @Query("""
        SELECT d FROM Device d
        WHERE d.deviceKey LIKE 'PS_PRD_SHV%'
           OR d.deviceKey LIKE 'PS_PRD_HV%'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchHighVoltageDevices();

    /** 전력 구간 — 저압 탭(저압반 LV). */
    @Query("""
        SELECT d FROM Device d
        WHERE d.deviceKey LIKE 'PS_PRD_LV%'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchLowVoltageDevices();

    /** 전력 구간 — 발전기 탭(비상발전기 GEN). */
    @Query("""
        SELECT d FROM Device d
        WHERE d.deviceKey LIKE 'PS_PGE_GEN%'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchGeneratorDevices();

//    @Query("""
//            SELECT d FROM Device d
//            LEFT JOIN FETCH d.devicePoints
//            """)
//    List<Device> fetchDeviceWithPoints();
}
