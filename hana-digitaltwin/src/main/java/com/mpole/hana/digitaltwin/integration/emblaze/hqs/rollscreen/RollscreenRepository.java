package com.mpole.hana.digitaltwin.integration.emblaze.hqs.rollscreen;

import com.mpole.hana.digitaltwin.device.entity.Device;
import jakarta.persistence.Tuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    /**
     * 제어 대상 — 전동롤스크린 <b>제어기</b>({@code MRS-CONTROL})의 {@code pos} 제어 포인트.
     *
     * <h3>조회(MRS)와 제어(MRS-CONTROL)는 다른 장비다</h3>
     * <p>상태를 보는 장비(모터, {@code F010_M001})와 명령을 받는 장비(구역 제어기,
     * {@code MT.F010.Z000})가 물리적으로 분리돼 있고 <b>DB 에도 둘을 잇는 관계가 없다.</b>
     * 그래서 제어는 제어기를 직접 지목해야 한다.</p>
     *
     * <p>{@code device_name} 은 유일하므로 <b>결과는 0건 아니면 1건</b>이다.
     * {@code floorNum} 을 같이 거는 건 층을 검증하기 위함이다 — HQS 가 보낸 층과
     * 실제 배치된 층이 다르면 제어하지 않는다(엉뚱한 층을 움직이는 것보다 거절이 낫다).</p>
     *
     * <p>반환 컬럼: {@code [0]command_point_id(Long) · [1]device_name}</p>
     *
     * @param floorNum  층 번호
     * @param groupName 제어기 {@code device_name}
     */
    @Query(value = """
            SELECT cp.command_point_id,
                   d.device_name
            FROM   tbl_device_command_point cp
            JOIN   tbl_device_point p        ON p.point_id    = cp.point_id
            JOIN   tbl_device d              ON d.device_id   = p.device_id
            JOIN   tbl_device_category c     ON c.category_id = d.category_id
            JOIN   tbl_device_placement pl   ON pl.device_id  = d.device_id
            JOIN   tbl_unity_loc_zone   z    ON z.zone_id     = pl.unity_zone_id
            JOIN   tbl_unity_loc_floor  f    ON f.floor_id    = z.floor_id
            WHERE  c.category_code = 'MRS-CONTROL'
              AND  p.tag_name = 'pos'
              AND  d.active = true
              AND  cp.active = true
              AND  f.floor_num = CAST(:floorNum AS int)
              AND  d.device_name = CAST(:groupName AS text)
            """, nativeQuery = true)
    Optional<Tuple> findControlPoint(@Param("floorNum") Integer floorNum,
                                     @Param("groupName") String groupName);
}
