package com.mpole.hana.digitaltwin.external.emblaze.write;

import com.mpole.hana.digitaltwin.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부스 재실 초기행 생성 — {@link BoothCurrentValueSeeder} 전용.
 *
 * <p>구 {@code DeviceWcBoothRepository} 를 쪼갠 것이다. 그 인터페이스에는 방향이 다른 쿼리가
 * 셋 섞여 있었다 — 초기행 생성(수신), 통계 장비 조회(푸시), 부스 재실 조회(HQS 응답).
 * 앞의 둘은 각자 쓰는 쪽으로 갔고, 부스 재실 조회는 그룹 기준으로 다시 쓴
 * {@code external.hqs.wc.HqsBoothRepository} 가 대신하면서 사라졌다.</p>
 */
@Repository
public interface BoothSeedRepository extends JpaRepository<Device, Long> {

    /**
     * 재실센서 장비의 {@code tbl_device_current_value} 행을 <b>없을 때만</b> {@code occ=0} 으로 만든다.
     *
     * <p>행이 항상 존재해야 {@code updated_at} 을 신선도 판단에 쓸 수 있다 — 행이 없으면
     * "값이 아직 안 온 것"과 "센서가 죽은 것"을 구분할 근거 자체가 없다.</p>
     *
     * <p>{@code ON CONFLICT DO NOTHING} 이라 <b>이미 값이 있는 부스는 절대 덮지 않는다.</b>
     * 기동 때마다 돌려도 안전하고, 장비를 새로 추가한 뒤 재기동하면 그 장비만 채워진다.</p>
     *
     * @return 실제로 새로 만든 행 수
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO tbl_device_current_value (device_id, device_key, points, created_at, updated_at)
            SELECT d.device_id,
                   d.device_key,
                   jsonb_build_array(jsonb_build_object(
                       'deviceId',       d.device_id,
                       'pointId',		 p.point_id,
                       'tagName',        p.tag_name,
                       'valueRaw',       '0',
                       'updateDatetime', to_jsonb(now()))),
                   now(), now()
            FROM tbl_device d
            JOIN tbl_device_point p ON d.device_id = p.device_id
            JOIN tbl_device_category c ON c.category_id = d.category_id
            WHERE c.category_code = 'OCC'
            AND d.active = true
            AND p.tag_name = 'occ'
            ON CONFLICT (device_id) DO NOTHING
            """, nativeQuery = true)
    int seedBoothOccupancyRows();
}
