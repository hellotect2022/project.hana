package com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen;

import com.mpole.hana.digitaltwin.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 전동롤스크린({@code ELEC > EE > MRS}) 조회 — HQS 연동 전용.
 */
@Repository
public interface RollscreenRepository extends JpaRepository<Device, Long> {

    /**
     * 층 식별자 해석. {@code florId} 로 층 번호({@code 10})와 층 이름({@code 10F}) 을 모두 받는다.
     *
     * <p>반환 컬럼: {@code [0]floor_num(Integer) · [1]floor_name}</p>
     */
    @Query(value = """
            SELECT f.floor_num, f.floor_name
            FROM   tbl_unity_loc_floor f
            WHERE  f.floor_name = CAST(:florId AS text)
               OR  CAST(f.floor_num AS text) = CAST(:florId AS text)
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> resolveFloor(@Param("florId") String florId);

    /**
     * 그 층의 롤스크린 + 현재 위치값.
     *
     * <h3>층 기준은 구역 배치 하나뿐이다</h3>
     * <p>{@code placement → zone → floor} 로만 찾는다. 그래서 <b>배치되지 않은 장비는 나오지 않는다</b>
     * — 위치를 모르는 장비를 특정 층 소속이라고 단정하지 않는다는 뜻이고, 의도한 동작이다.</p>
     *
     * <p>{@code device_name} 이 {@code F010_M001} 처럼 층을 담고 있어 이름으로 보완할 수도 있지만
     * 쓰지 않는다 — 이름 규칙이 바뀌면 조용히 틀리고, 배치가 정본인데 두 기준이 생기면
     * 어느 쪽이 맞는지 알 수 없게 된다. 배치 데이터를 채우는 것이 해결책이다.</p>
     *
     * <p>현재 전동롤스크린 2,413대 중 배치된 것은 615대(4F·10F·14F)다.
     * <b>나머지 층은 배치 전까지 빈 목록이 나간다.</b></p>
     *
     * <p>반환 컬럼: {@code [0]device_id(Long) · [1]device_name · [2]pos_st(String)}</p>
     */
    @Query(value = """
            SELECT d.device_id,
                   d.device_name,
                   (SELECT e->>'valueRaw'
                      FROM jsonb_array_elements(COALESCE(cv.points, '[]'::jsonb)) AS e
                     WHERE e->>'tagName' = 'pos-st'
                     LIMIT 1) AS pos_st
            FROM   tbl_device d
            JOIN   tbl_device_category c     ON c.category_id = d.category_id
            JOIN   tbl_device_placement pl   ON pl.device_id  = d.device_id
            JOIN   tbl_unity_loc_zone   z    ON z.zone_id     = pl.unity_zone_id
            JOIN   tbl_unity_loc_floor  f    ON f.floor_id    = z.floor_id
            LEFT JOIN tbl_device_current_value cv ON cv.device_id = d.device_id
            WHERE  c.category_code = 'MRS'
              AND  d.active = true
              AND  f.floor_num = CAST(:floorNum AS int)
            ORDER BY d.device_name
            """, nativeQuery = true)
    List<Object[]> findRollscreensByFloor(@Param("floorNum") Integer floorNum);
}
