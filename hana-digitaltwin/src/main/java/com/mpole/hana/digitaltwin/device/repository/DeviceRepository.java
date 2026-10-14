package com.mpole.hana.digitaltwin.device.repository;

import com.mpole.hana.digitaltwin.device.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
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
        LEFT JOIN FETCH c.defaultAsset
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

    @Query(
            value = """
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.placement p
            LEFT JOIN FETCH p.unityZone z
            LEFT JOIN FETCH d.category c
            LEFT JOIN FETCH c.defaultAsset
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(d.deviceKey, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(c.categoryName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR CONCAT('', d.deviceId) LIKE CONCAT('%', :keyword, '%'))
              AND (:categoryIds IS NULL OR c.categoryId IN :categoryIds)
              AND (:hasCommandPoint IS NULL OR :hasCommandPoint = FALSE
                OR EXISTS (SELECT 1 FROM CommandPoint cp
                            WHERE cp.device = d AND cp.active = TRUE))
            """,
            countQuery = """
            SELECT COUNT(DISTINCT d) FROM Device d
            LEFT JOIN d.category c
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(d.deviceKey, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(c.categoryName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR CONCAT('', d.deviceId) LIKE CONCAT('%', :keyword, '%'))
              AND (:categoryIds IS NULL OR c.categoryId IN :categoryIds)
              AND (:hasCommandPoint IS NULL OR :hasCommandPoint = FALSE
                OR EXISTS (SELECT 1 FROM CommandPoint cp
                            WHERE cp.device = d AND cp.active = TRUE))
            """
    )
    Page<Device> searchDevices(
            @Param("keyword") String keyword,
            @Param("categoryIds") Collection<Long> categoryIds,
            /**
             * true 면 <b>활성 제어 포인트가 있는 장비만</b> 반환한다(디바이스 제어 화면 전용).
             * null/false 면 조건에서 빠져 기존 동작 그대로다 — 다른 소비처는 영향받지 않는다.
             * 클라이언트에서 거르면 페이지당 20건을 받아 몇 건만 남아 페이징이 깨지므로 서버에서 건다.
             */
            @Param("hasCommandPoint") Boolean hasCommandPoint,
            Pageable pageable
    );

    /**
     * 층·구역 기준 장비 조회.
     *
     * <p><b>구역은 단건이 아니라 목록을 받는다</b> — 구역에 계층이 생겨서(화장실 &gt; 부스)
     * 화장실을 고르면 그 안 부스에 배치된 장비도 나와야 하기 때문이다.
     * 호출부가 {@code ZoneCascadeService.resolveSubtreeZoneIds()} 로 펼쳐 넘긴다.</p>
     *
     * @param zoneIds null 이면 구역 필터 없음. <b>빈 목록을 넘기지 말 것</b> —
     *                {@code IN ()} 은 SQL 오류다. 호출부에서 0건으로 단락시킨다
     */
    @Query("""
        SELECT d FROM Device d
        LEFT JOIN FETCH d.placement p
        LEFT JOIN FETCH p.unityZone z
        WHERE (:floorId IS NULL OR z.floor.floorId = :floorId)
          AND (:zoneIds IS NULL OR z.zoneId IN :zoneIds)
        ORDER BY d.deviceId ASC
        """)
    List<Device> fetchSearchDevices(@Param("floorId") Long floorId,
                                    @Param("zoneIds") Collection<Long> zoneIds);

    @Query("""
            SELECT d 
            FROM Device d
            LEFT JOIN FETCH d.placement p
            LEFT JOIN FETCH p.unityZone z
            LEFT JOIN FETCH d.category c
            LEFT JOIN FETCH c.defaultAsset
            WHERE c.categoryId = :categoryId
            ORDER BY d.deviceId ASC
            """)
    Page<Device> fetchTargetCategoryDevices(@Param("categoryId") Long categoryId,Pageable pageable);

    @Query("""
            SELECT DISTINCT d
            FROM Device d
            LEFT JOIN FETCH d.category c
            WHERE c.categoryId IN :categoryIds
            ORDER BY d.deviceId ASC
            """)
    Page<Device> fetchByCategoryIds(@Param("categoryIds") Collection<Long> categoryIds, Pageable pageable);

    long countByCategory_CategoryId(Long categoryId);

    boolean existsByDeviceKey(String deviceKey);
 
    boolean existsByDeviceKeyAndDeviceIdNot(String deviceKey, Long deviceId);

    List<Device> findByCategory_CategoryIdIn(Collection<Long> categoryIds);

    @Query("""
            SELECT d.deviceKey FROM Device d
            WHERE d.deviceKey IN :keys
              AND d.deviceId NOT IN :excludeDeviceIds
            """)
    List<String> findConflictingDeviceKeys(
            @Param("keys") Collection<String> keys,
            @Param("excludeDeviceIds") Collection<Long> excludeDeviceIds
    );

    //region 🔄 디바이스 활성/비활성화 하는 repo 들
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Device d SET d.active = :active, d.updatedAt = :now")
    int updateActiveAll(@Param("active") Boolean active, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Device d SET d.active = :active, d.updatedAt = :now WHERE d.category.categoryId IN :ids")
    int updateActiveByCategoryIds(@Param("ids") List<Long> ids,
                                  @Param("active") Boolean active,
                                  @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Device d SET d.active = :active, d.updatedAt = :now WHERE d.deviceId IN :ids")
    int updateActiveByDeviceIds(@Param("ids") List<Long> ids,
                                @Param("active") Boolean active,
                                @Param("now") OffsetDateTime now);
    //endregion



    // 디바이스 정보 변경후에 카테고리 bulk 로 변경하는 로직
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE tbl_device d
            SET device_key = TRIM(maj.category_code) || '_' || TRIM(mid.category_code) || '_' || TRIM(small.category_code)  || '_' || TRIM(d.device_name)
                ,
                updated_at = NOW()
            FROM tbl_device_category small
            JOIN tbl_device_category mid ON small.category_parent_id = mid.category_id
            JOIN tbl_device_category maj ON mid.category_parent_id = maj.category_id
            WHERE d.category_id = small.category_id
              AND small.depth = 2
              AND small.category_id IN (:smallCategoryIds)
            """, nativeQuery = true)
    int bulkRebuildDeviceKeysForSmallCategoriesIn(@Param("smallCategoryIds") Collection<Long> smallCategoryIds);
    
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

    /** MCC 및 동력 계통도 — PS_MCC_% 설비 전체 */
    @Query("""
        SELECT d FROM Device d
        WHERE d.deviceKey LIKE 'PS_MCC_%'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchMccDiagramDevices();

    /** 수변전 설비 계통도 — SHV·HV·GEN·LV (계측 패널·LV hotspot id 매핑) */
    @Query("""
        SELECT d FROM Device d
        WHERE d.deviceKey LIKE 'PS_PRD_SHV%'
           OR d.deviceKey LIKE 'PS_PRD_HV%'
           OR d.deviceKey LIKE 'PS_PGE_GEN%'
           OR d.deviceKey LIKE 'PS_PRD_LV%'
        ORDER BY d.deviceKey ASC
        """)
    List<Device> fetchSubstationDiagramDevices();

    /** 소방 — 화재감지기(DET) 등록 대수 */
    @Query("""
        SELECT COUNT(d) FROM Device d
        JOIN d.category c
        WHERE c.categoryCode = 'DET'
        """)
    long countFireDetectorDevices();

//    @Query("""
//            SELECT d FROM Device d
//            LEFT JOIN FETCH d.devicePoints
//            """)
//    List<Device> fetchDeviceWithPoints();
}
