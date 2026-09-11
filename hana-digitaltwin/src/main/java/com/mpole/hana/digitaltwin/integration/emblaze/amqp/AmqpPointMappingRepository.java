package com.mpole.hana.digitaltwin.integration.emblaze.amqp;

import com.mpole.hana.digitaltwin.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@code data_source = 'AMQP'} 포인트 매핑 적재 — {@link AmqpPointMappingCache} 전용.
 */
@Repository
public interface AmqpPointMappingRepository extends JpaRepository<Device, Long> {

    /**
     * RabbitMQ 로 채워지는 포인트 전량 + 배치된 구역/층 정보.
     *
     * <p>반환 컬럼 순서:
     * {@code [0]ref_device_code · [1]ref_point_code · [2]tag_name · [3]device_id · [4]device_key ·
     * [5]category_code · [6]zone_id · [7]zone_code · [8]zone_name ·
     * [9]parent_zone_code · [10]parent_zone_name · [11]floor_name}</p>
     *
     * <p>{@code ref_point_code} 가 비어 있는 포인트는 제외한다 — 벤더가 보내주는 필드가 아니라
     * 우리가 계산해 넣는 파생값({@code occ})이라 라우팅 대상이 아니다.</p>
     */
    @Query(value = """
            SELECT LOWER(p.ref_device_code) AS ref_device_code,
                   p.ref_point_code,
                   p.tag_name,
                   d.device_id,
                   d.device_key,
                   c.category_code,
                   z.zone_id,
                   z.zone_code,
                   z.zone_name,
                   pz.zone_code,
                   pz.zone_name,
                   f.floor_name
            FROM tbl_device_point p
            JOIN tbl_device d           ON d.device_id   = p.device_id
            JOIN tbl_device_category c  ON c.category_id = d.category_id
            LEFT JOIN tbl_device_placement pl ON pl.device_id = d.device_id
            LEFT JOIN tbl_unity_loc_zone   z  ON z.zone_id    = pl.unity_zone_id
            LEFT JOIN tbl_unity_loc_zone   pz ON pz.zone_id   = z.zone_parent_id
            LEFT JOIN tbl_unity_loc_floor  f  ON f.floor_id   = z.floor_id
            WHERE p.data_source = 'AMQP'
              AND p.active = true
              AND d.active = true
              AND COALESCE(p.ref_device_code, '') <> ''
              AND COALESCE(p.ref_point_code, '') <> ''
            """, nativeQuery = true)
    List<Object[]> findAmqpPointMappings();
}
