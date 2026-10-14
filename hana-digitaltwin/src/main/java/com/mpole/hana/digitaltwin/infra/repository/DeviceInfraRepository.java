package com.mpole.hana.digitaltwin.infra.repository;

import com.mpole.hana.common.constant.InfraType;
import com.mpole.hana.digitaltwin.infra.entity.DeviceInfra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DeviceInfraRepository extends JpaRepository<DeviceInfra, Long> {

    boolean existsByRefInfraCode(String refInfraCode);

    List<DeviceInfra> findAllByInfraTypeOrderByInfraNameAsc(InfraType infraType);

    List<DeviceInfra> findAllByOrderByInfraTypeAscInfraNameAsc();

    /** 인프라별 매핑 장비 수(관리 화면 표시용). */
    interface InfraDeviceCountRow {
        Long getInfraId();
        Long getCnt();
    }

    @Query(value = """
            SELECT d.infra_id AS "infraId", COUNT(*) AS "cnt"
            FROM tbl_device d
            WHERE d.infra_id IS NOT NULL
            GROUP BY d.infra_id
            """, nativeQuery = true)
    List<InfraDeviceCountRow> countDevicesByInfra();

    /**
     * 장비→인프라 자동 산정(설계 v2.2): 포인트 ref_device_code 최빈 → ref_infra_code 매칭.
     * <p>동률 tie-break: count DESC, ref_device_code ASC(결정적). excludeCsv 는 공유신호(브로드캐스트)
     * 인프라 코드 제외 목록(콤마 구분, 빈 문자열=제외 없음).</p>
     * <p><b>infra_id IS NULL 인 장비만 채운다(수동 우선 — 자동이 수동 지정을 덮지 않음).</b></p>
     */
    @Modifying
    @Query(value = """
            UPDATE tbl_device d SET infra_id = pick.infra_id, updated_at = NOW()
            FROM (
                SELECT m.device_id, i.infra_id
                FROM (
                    SELECT p.device_id, p.ref_device_code,
                           ROW_NUMBER() OVER (PARTITION BY p.device_id ORDER BY COUNT(*) DESC, p.ref_device_code ASC) AS rn
                    FROM tbl_device_point p
                    WHERE p.ref_device_code IS NOT NULL
                      AND (COALESCE(:excludeCsv, '') = '' OR p.ref_device_code <> ALL (STRING_TO_ARRAY(:excludeCsv, ',')))
                    GROUP BY p.device_id, p.ref_device_code
                ) m
                JOIN tbl_device_infra i ON i.ref_infra_code = m.ref_device_code AND i.active = true
                WHERE m.rn = 1
            ) pick
            WHERE d.device_id = pick.device_id
            AND d.infra_id IS NULL
            """, nativeQuery = true)
    int autoAssignAll(@Param("excludeCsv") String excludeCsv);

    /** 자동 산정 — 특정 장비들로 한정(포인트 ref 리매핑 훅용). 규칙은 autoAssignAll 과 동일. */
    @Modifying
    @Query(value = """
            UPDATE tbl_device d SET infra_id = pick.infra_id, updated_at = NOW()
            FROM (
                SELECT m.device_id, i.infra_id
                FROM (
                    SELECT p.device_id, p.ref_device_code,
                           ROW_NUMBER() OVER (PARTITION BY p.device_id ORDER BY COUNT(*) DESC, p.ref_device_code ASC) AS rn
                    FROM tbl_device_point p
                    WHERE p.ref_device_code IS NOT NULL
                      AND p.device_id IN (:deviceIds)
                    GROUP BY p.device_id, p.ref_device_code
                ) m
                JOIN tbl_device_infra i ON i.ref_infra_code = m.ref_device_code AND i.active = true
                WHERE m.rn = 1
            ) pick
            WHERE d.device_id = pick.device_id
              AND d.infra_id IS NULL
            """, nativeQuery = true)
    int autoAssignForDevices(@Param("deviceIds") Collection<Long> deviceIds);

    /** 인프라 기준 매핑 장비 목록(관리 화면). */
    interface MappedDeviceRow {
        Long getDeviceId();
        String getDeviceName();
        String getDeviceDisplayName();
        String getCategoryName();
    }

    @Query(value = """
            SELECT d.device_id AS "deviceId", d.device_name AS "deviceName",
                   d.device_display_name AS "deviceDisplayName", c.category_name AS "categoryName"
            FROM tbl_device d
            LEFT JOIN tbl_device_category c ON c.category_id = d.category_id
            WHERE d.infra_id = :infraId
            ORDER BY d.device_name
            """, nativeQuery = true)
    List<MappedDeviceRow> findMappedDevices(@Param("infraId") Long infraId);

    /** 매핑 후보(이 인프라 소속이 아닌 장비, 키워드 검색). 타 인프라 소속이면 현재 소속을 함께 반환. */
    interface CandidateDeviceRow {
        Long getDeviceId();
        String getDeviceName();
        String getDeviceDisplayName();
        String getCategoryName();
        Long getInfraId();
        String getInfraName();
    }

    @Query(value = """
            SELECT d.device_id AS "deviceId", d.device_name AS "deviceName",
                   d.device_display_name AS "deviceDisplayName", c.category_name AS "categoryName",
                   d.infra_id AS "infraId", i.infra_name AS "infraName"
            FROM tbl_device d
            LEFT JOIN tbl_device_category c ON c.category_id = d.category_id
            LEFT JOIN tbl_device_infra i ON i.infra_id = d.infra_id
            WHERE (d.infra_id IS NULL OR d.infra_id <> :infraId)
              AND (:keyword = '' OR d.device_name ILIKE '%' || :keyword || '%'
                                 OR d.device_display_name ILIKE '%' || :keyword || '%')
            ORDER BY d.device_name
            LIMIT 100
            """, nativeQuery = true)
    List<CandidateDeviceRow> findMappingCandidates(@Param("infraId") Long infraId, @Param("keyword") String keyword);

    /** 인프라 기준 일괄 매핑(타 인프라 소속 장비도 이 인프라로 이동 — 수동 지정은 항상 우선/명시적). */
    @Modifying
    @Query(value = """
            UPDATE tbl_device SET infra_id = :infraId, updated_at = NOW()
            WHERE device_id IN (:deviceIds)
            """, nativeQuery = true)
    int bulkAssignDevices(@Param("infraId") Long infraId, @Param("deviceIds") Collection<Long> deviceIds);

    /** 인프라 기준 일괄 해제(이 인프라 소속인 장비만 해제). */
    @Modifying
    @Query(value = """
            UPDATE tbl_device SET infra_id = NULL, updated_at = NOW()
            WHERE infra_id = :infraId AND device_id IN (:deviceIds)
            """, nativeQuery = true)
    int bulkUnassignDevices(@Param("infraId") Long infraId, @Param("deviceIds") Collection<Long> deviceIds);

    /**
     * 인프라 삭제 전 소속 장비 매핑 전량 해제(infra_id=NULL).
     * <p>FK 는 ON DELETE SET NULL 이지만, 마이그레이션 SQL 미적용 등으로 FK 가 없는 DB 에서도
     * 장비 행이 지워지거나 고아 참조가 남지 않도록 애플리케이션에서 명시 해제한다.
     * <b>장비 행 자체는 절대 삭제하지 않는다.</b></p>
     */
    @Modifying
    @Query(value = """
            UPDATE tbl_device SET infra_id = NULL, updated_at = NOW()
            WHERE infra_id = :infraId
            """, nativeQuery = true)
    int clearInfraFromAllDevices(@Param("infraId") Long infraId);

    /** 정합성: XN ref 포인트를 갖고 있으면서 인프라 미매핑(infra_id IS NULL)인 장비. */
    interface UnmappedDeviceRow {
        Long getDeviceId();
        String getDeviceName();
    }

    @Query(value = """
            SELECT d.device_id AS "deviceId", d.device_name AS "deviceName"
            FROM tbl_device d
            WHERE d.infra_id IS NULL
              AND EXISTS (
                  SELECT 1 FROM tbl_device_point p
                  WHERE p.device_id = d.device_id AND p.ref_device_code IS NOT NULL
              )
            ORDER BY d.device_name
            """, nativeQuery = true)
    List<UnmappedDeviceRow> findUnmappedXnDevices();

    /** 정합성: 서로 다른 ref_device_code 를 2개 이상 참조하는 장비(공유신호/오귀속 검수 후보). */
    interface MultiRefDeviceRow {
        Long getDeviceId();
        String getDeviceName();
        Long getRefCount();
    }

    @Query(value = """
            SELECT d.device_id AS "deviceId", d.device_name AS "deviceName",
                   COUNT(DISTINCT p.ref_device_code) AS "refCount"
            FROM tbl_device d
            JOIN tbl_device_point p ON p.device_id = d.device_id AND p.ref_device_code IS NOT NULL
            GROUP BY d.device_id, d.device_name
            HAVING COUNT(DISTINCT p.ref_device_code) > 1
            ORDER BY COUNT(DISTINCT p.ref_device_code) DESC, d.device_name
            """, nativeQuery = true)
    List<MultiRefDeviceRow> findMultiRefDevices();
}
