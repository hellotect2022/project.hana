package com.mpole.hana.digitaltwin.external.hqs.charger;

import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * HQS 전기차 충전기 조회 — <b>장비 그룹 기준</b>.
 *
 * <p>충전기는 카테고리 {@code ELEC > EV > DCC(급속) / ACC(완속)} 의 장비이고,
 * 충전소는 {@code tbl_device_group(group_type='EV_STATION')} 이다.</p>
 *
 * <h3>예전에는 충전소가 구역(zone)이었다</h3>
 * <pre>
 *   이전  stationId = zone_code,  충전기 = 그 구역(+하위 구역)에 배치된 장비
 *   현재  stationId = group_code, 충전기 = 그 그룹의 멤버
 * </pre>
 *
 * <p>부스·롤스크린을 그룹으로 옮긴 것과 같은 이유다. zone 은 3D 콜라이더가 정본이라
 * 도면 작업에 묶이고, 구역 이름이 업무 이름과 다르다 — 실제로 충전소 구역의
 * {@code zone_name} 이 {@code "주차장"} 이라 충전소 이름으로 쓸 수 없었다.
 * 그룹은 업무 단위가 곧 정본이고 관리자 화면에서 편집된다.</p>
 *
 * <p>{@code group_code} 는 구 {@code zone_code} 와 같은 값({@code EVCS_B2F_01})이라
 * <b>HQS 가 쓰는 stationId 는 바뀌지 않는다.</b> 바뀌는 것은 stationName 뿐이다
 * ({@code 주차장} → {@code 전기차충전소_지하2층}).</p>
 *
 * <h3>배치를 보지 않는다</h3>
 * <p>예전 구현은 배치가 없는 충전기를 "소속 충전소를 알 수 없다"며 응답에서 뺐다.
 * 이제 소속은 그룹 멤버십이 말해주므로 <b>배치와 무관하게</b> 나간다.
 * 한 충전소가 여러 구역에 걸쳐도 {@code ZoneCascadeService} 로 서브트리를 뒤질 필요가 없다.</p>
 */
@Repository
public interface ChargerRepository extends JpaRepository<DeviceGroup, Long> {

    /** 급속·완속 충전기 소분류 코드 */
    String CATEGORY_FAST = "DCC";
    String CATEGORY_SLOW = "ACC";

    /**
     * 충전소별 충전기 목록. 한 줄이 충전기 1대다.
     *
     * <p>{@code role} 을 보지 않는다 — 조회 전용이라 갈라 잡을 이유가 없고,
     * 현재 충전기 멤버의 role 은 전부 비어 있다. 나중에 원격 제어가 붙으면 그때
     * {@code CONTROLLER} 를 넣고 제어 쿼리에서만 쓰면 된다.</p>
     *
     * <h3>{@code updateDatetime} 을 문자열로 내보내는 이유</h3>
     * <p>JSONB 안의 값이라 형식이 깨져 있을 수 있는데, SQL 에서 {@code ::timestamptz} 로
     * 캐스팅하면 그 한 행 때문에 <b>쿼리 전체가 실패</b>한다. 문자열로 받아 자바에서
     * 파싱하고, 실패하면 행 갱신시각으로 떨어뜨린다.</p>
     *
     * <p>반환 컬럼: {@code [0]station_id · [1]station_name · [2]category_code ·
     * [3]charger_id · [4]charger_name · [5]st_value · [6]st_updated(text) · [7]row_updated_at}</p>
     *
     * @param stationId 충전소 {@code group_code}. {@code null} 이면 전 충전소
     */
    @Query(value = """
            SELECT g.group_code,
                   g.group_name,
                   cat.category_code,
                   d.device_key,
                   d.device_name,
                   st.value_raw,
                   st.updated_text,
                   cv.updated_at
            FROM   tbl_device_group g
            JOIN   tbl_device_group_member m ON m.group_id   = g.group_id
            JOIN   tbl_device d              ON d.device_id  = m.device_id AND d.active = true
            JOIN   tbl_device_category cat   ON cat.category_id = d.category_id
            LEFT   JOIN tbl_device_current_value cv ON cv.device_id = d.device_id
            LEFT   JOIN LATERAL (
                     SELECT e->>'valueRaw'       AS value_raw,
                            e->>'updateDatetime' AS updated_text
                     FROM   jsonb_array_elements(COALESCE(cv.points, '[]'::jsonb)) AS e
                     WHERE  lower(e->>'tagName') = 'st'
                     LIMIT  1
                   ) st ON true
            WHERE  g.group_type = 'EV_STATION'
              AND  g.active = true
              AND (CAST(:stationId AS text) IS NULL
                   OR g.group_code = CAST(:stationId AS text))
            ORDER  BY g.sort_order, g.group_code, cat.category_code, d.device_id
            """, nativeQuery = true)
    List<Object[]> findChargersByStation(@Param("stationId") String stationId);
}
