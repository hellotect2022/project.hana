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

    @Query(
            value = """
            SELECT DISTINCT d FROM Device d
            LEFT JOIN FETCH d.placement p
            LEFT JOIN FETCH p.unityZone z
            LEFT JOIN FETCH d.category c
            LEFT JOIN FETCH d.unityAsset
            WHERE (:keyword IS NULL OR :keyword = ''
                OR LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(d.deviceKey, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(c.categoryName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR CONCAT('', d.deviceId) LIKE CONCAT('%', :keyword, '%'))
              AND (:categoryIds IS NULL OR c.categoryId IN :categoryIds)
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
            """
    )
    Page<Device> searchDevices(
            @Param("keyword") String keyword,
            @Param("categoryIds") Collection<Long> categoryIds,
            Pageable pageable
    );

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

    long countByUnityAsset_AssetId(Long assetId);

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

//    @Query("""
//            SELECT d FROM Device d
//            LEFT JOIN FETCH d.devicePoints
//            """)
//    List<Device> fetchDeviceWithPoints();
}
