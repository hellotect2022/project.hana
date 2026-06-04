package com.mpole.hana.digitaltwin.device.repository;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DevicePointRepository extends JpaRepository<DevicePoint,Long> {

    boolean existsByPointKey(String pointKey);

    boolean existsByPointKeyAndPointIdNot(String pointKey, Long pointId);

    boolean existsByDevice_DeviceIdAndTagName(Long deviceId, String tagName);

    java.util.Optional<DevicePoint> findByDevice_DeviceIdAndTagName(Long deviceId, String tagName);

    void deleteByDevice_DeviceIdAndTagNameIn(Long deviceId, Collection<String> tagNames);

    List<DevicePoint> findAllByDevice_DeviceId(Long deviceId);

    List<DevicePoint> findAllByDevice_DeviceIdOrderByTagNameAsc(Long deviceId);

    void deleteByDevice_DeviceIdIn(Collection<Long> deviceIds);

    @Query("""
            SELECT p FROM DevicePoint p
            JOIN FETCH p.device d
            WHERE d.deviceId IN :deviceIds
            """)
    List<DevicePoint> findAllByDevice_DeviceIdIn(@Param("deviceIds") Collection<Long> deviceIds);

    @Query("""
            SELECT p.pointKey FROM DevicePoint p
            WHERE p.pointKey IN :keys
              AND p.pointId NOT IN :excludePointIds
            """)
    List<String> findConflictingPointKeys(
            @Param("keys") Collection<String> keys,
            @Param("excludePointIds") Collection<Long> excludePointIds
    );

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE tbl_device_point p
            SET point_key = d.device_key || '_' || p.tag_name,
                updated_at = NOW()
            FROM tbl_device d
            WHERE p.device_id = d.device_id
              AND d.category_id IN (:smallCategoryIds)
            """, nativeQuery = true)
    int bulkRebuildPointKeysForDevicesInSmallCategories(
            @Param("smallCategoryIds") Collection<Long> smallCategoryIds
    );

    Optional<DevicePoint> findByRefDeviceCodeAndRefPointCode(String refDeviceCode, String refPointCode);

    Optional<DevicePoint> findByRefDeviceCodeAndRefPointCodeAndPointIdNot(
            String refDeviceCode,
            String refPointCode,
            Long pointId
    );

    @Query("""
            SELECT p FROM DevicePoint p
            JOIN FETCH p.device d
            LEFT JOIN FETCH d.category
            WHERE (:deviceId IS NULL OR d.deviceId = :deviceId)
              AND (
                  :keyword IS NULL OR :keyword = ''
                  OR LOWER(p.tagName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(p.pointName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(p.pointKey) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(p.refDeviceCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(p.refPointCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY d.deviceName ASC, p.tagName ASC
            """)
    List<DevicePoint> findForRefMapping(
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword
    );

    @Query(
            value = """
            SELECT DISTINCT p FROM DevicePoint p
            JOIN FETCH p.device d
            LEFT JOIN FETCH d.category
            WHERE (:deviceId IS NULL OR d.deviceId = :deviceId)
              AND (
                  :keyword IS NULL OR :keyword = ''
                  OR LOWER(p.tagName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(p.pointName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(p.pointKey) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(p.refDeviceCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(p.refPointCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p) FROM DevicePoint p
            JOIN p.device d
            WHERE (:deviceId IS NULL OR d.deviceId = :deviceId)
              AND (
                  :keyword IS NULL OR :keyword = ''
                  OR LOWER(p.tagName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(p.pointName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(p.pointKey) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(p.refDeviceCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(p.refPointCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """
    )
    Page<DevicePoint> findForRefMappingPage(
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(DISTINCT p) FROM DevicePoint p
            JOIN p.device d
            WHERE (:deviceId IS NULL OR d.deviceId = :deviceId)
              AND (
                  :keyword IS NULL OR :keyword = ''
                  OR LOWER(p.tagName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(p.pointName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(p.pointKey) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(d.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(p.refDeviceCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                  OR LOWER(COALESCE(p.refPointCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
              AND p.refDeviceCode IS NOT NULL AND p.refDeviceCode <> ''
              AND p.refPointCode IS NOT NULL AND p.refPointCode <> ''
            """)
    long countMappedForRefMapping(
            @Param("deviceId") Long deviceId,
            @Param("keyword") String keyword
    );

    @Modifying(clearAutomatically = true)
    @Query(value = """
            DELETE FROM tbl_device_point p
            USING tbl_device d
            WHERE p.device_id = d.device_id
              AND d.category_id = :categoryId
              AND p.tag_name IN (:tagNames)
            """, nativeQuery = true)
    int bulkDeleteByCategoryIdAndTagNameIn(
            @Param("categoryId") Long categoryId,
            @Param("tagNames") Collection<String> tagNames
    );

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE tbl_device_point p
            SET tag_name = :newTag,
                point_key = d.device_key || '_' || :newTag,
                point_name = :pointName,
                point_type = :pointType,
                unit = :unit,
                active = :active,
                updated_at = NOW()
            FROM tbl_device d
            WHERE p.device_id = d.device_id
              AND d.category_id = :categoryId
              AND p.tag_name = :oldTag
            """, nativeQuery = true)
    int bulkRenameByCategoryId(
            @Param("categoryId") Long categoryId,
            @Param("oldTag") String oldTag,
            @Param("newTag") String newTag,
            @Param("pointName") String pointName,
            @Param("pointType") String pointType,
            @Param("unit") String unit,
            @Param("active") boolean active
    );

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE tbl_device_point p
            SET point_name = :pointName,
                point_type = :pointType,
                unit = :unit,
                active = :active,
                updated_at = NOW()
            FROM tbl_device d
            WHERE p.device_id = d.device_id
              AND d.category_id = :categoryId
              AND p.tag_name = :tagName
            """, nativeQuery = true)
    int bulkUpdateSchemaFieldsByCategoryIdAndTagName(
            @Param("categoryId") Long categoryId,
            @Param("tagName") String tagName,
            @Param("pointName") String pointName,
            @Param("pointType") String pointType,
            @Param("unit") String unit,
            @Param("active") boolean active
    );

    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO tbl_device_point (
                device_id, point_key, tag_name, point_name, point_type, unit, active,
                ref_device_code, ref_point_code, created_at, updated_at
            )
            SELECT
                d.device_id,
                d.device_key || '_' || :tagName,
                :tagName,
                :pointName,
                :pointType,
                :unit,
                :active,
                NULL,
                NULL,
                NOW(),
                NOW()
            FROM tbl_device d
            WHERE d.category_id = :categoryId
              AND d.device_key IS NOT NULL
              AND TRIM(d.device_key) <> ''
              AND NOT EXISTS (
                  SELECT 1 FROM tbl_device_point p
                  WHERE p.device_id = d.device_id AND p.tag_name = :tagName
              )
            """, nativeQuery = true)
    int bulkInsertByCategoryIdAndTag(
            @Param("categoryId") Long categoryId,
            @Param("tagName") String tagName,
            @Param("pointName") String pointName,
            @Param("pointType") String pointType,
            @Param("unit") String unit,
            @Param("active") boolean active
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM tbl_device_point p
                JOIN tbl_device d ON p.device_id = d.device_id
                JOIN tbl_device_point p2 ON p2.point_key = (d.device_key || '_' || :newTag)
                WHERE d.category_id = :categoryId
                  AND p.tag_name = :oldTag
                  AND p2.point_id <> p.point_id
            )
            """, nativeQuery = true)
    boolean existsPointKeyConflictOnRename(
            @Param("categoryId") Long categoryId,
            @Param("oldTag") String oldTag,
            @Param("newTag") String newTag
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM tbl_device d
                WHERE d.category_id = :categoryId
                  AND d.device_key IS NOT NULL
                  AND TRIM(d.device_key) <> ''
                  AND NOT EXISTS (
                      SELECT 1 FROM tbl_device_point p
                      WHERE p.device_id = d.device_id AND p.tag_name = :tagName
                  )
                  AND EXISTS (
                      SELECT 1 FROM tbl_device_point p2
                      WHERE p2.point_key = d.device_key || '_' || :tagName
                  )
            )
            """, nativeQuery = true)
    boolean existsPointKeyConflictOnInsert(
            @Param("categoryId") Long categoryId,
            @Param("tagName") String tagName
    );
}
