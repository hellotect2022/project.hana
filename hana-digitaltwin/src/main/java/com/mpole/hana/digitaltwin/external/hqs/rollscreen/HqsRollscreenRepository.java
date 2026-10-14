package com.mpole.hana.digitaltwin.external.hqs.rollscreen;

import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroup;
import jakarta.persistence.Tuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 전동롤스크린 — <b>장비 그룹 기준</b>. HQS {@code /api/v1/hqs/rollscreen/**} 전용.
 *
 * <p>예전 구현({@code RollscreenRepository})과 무엇이 달라지는가.</p>
 *
 * <pre>
 *   이전  groupId = 모터 device_id   한 줄 = 모터 1대   (층에 배치된 모터를 전부 나열)
 *   현재  groupId = group_code       한 줄 = 구역 1개
 * </pre>
 *
 * <p>예전 구현은 "그룹"이라는 이름을 쓰면서 실제로는 모터 하나하나를 내보냈다. 구역이라는 개념이
 * DB 에 없었기 때문이다. 이제 그룹이 있으므로 <b>HQS 가 기대하는 대로 구역 단위</b>로 나간다.</p>
 *
 * <h3>조회와 제어가 같은 장비를 본다</h3>
 * <p>구역에는 모터({@code MRS})와 구역 제어기({@code MRS-CONTROL})가 함께 묶여 있다.
 * 예전 구현은 이 둘을 잇는 관계가 DB 에 없어 제어기를 이름으로 직접 지목했다.
 * 지금은 <b>같은 그룹 안에서 {@code role} 로 갈라</b> 집는다 — 카테고리 코드를 쿼리에 박지 않는다.</p>
 *
 * <pre>
 *   조회  role = 'CONTROLLER'   → points 의 pos          (그룹당 1대라 집계 불필요)
 *   제어  role = 'CONTROLLER'   → command_point 의 pos
 * </pre>
 *
 * <p>모터({@code MONITOR})의 {@code pos-st} 를 쓰지 않는 이유는 {@link #findGroupsByFloor} 주석 참고.</p>
 *
 * <p>{@code attributes.controlRef} 에도 제어기 이름이 들어 있지만 쓰지 않는다 —
 * 멤버 테이블이 정본이고, 두 곳을 다 보면 어긋났을 때 어느 쪽이 맞는지 알 수 없다.</p>
 */
@Repository
public interface HqsRollscreenRepository extends JpaRepository<DeviceGroup, Long> {

    /**
     * 층 식별자 해석. 층 번호({@code 3})와 층 이름({@code 3F}) 을 모두 받는다.
     *
     * <p>HQS 가 어느 형식으로 보낼지 확정되지 않았고, 한쪽만 받으면 형식이 어긋나는 순간
     * <b>조용히 빈 배열</b>이 나간다.</p>
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
     * 그 층의 롤스크린 구역과 개방률.
     *
     * <h3>비활성 그룹은 빼고 준다</h3>
     * <p>{@code g.active = true} — 모터 배정이 끝나지 않은 층을 꺼 두면 HQS 에 빈 구역이
     * 줄줄이 나가지 않는다. 현재 3층만 켜져 있다.</p>
     *
     * <h3>값은 제어기({@code CONTROLLER})의 {@code pos} 에서 읽는다</h3>
     * <p>제어기는 <b>그룹당 정확히 1대</b>라 집계가 필요 없다. 한때 모터({@code MONITOR})들의
     * {@code pos-st} 를 최빈값으로 묶었는데, 그 방식은 <b>모터가 배정되지 않은 구역을 통째로
     * 누락</b>시켰다 — 4층 17개 구역이 INNER JOIN 에서 탈락해 37개 중 20개만 나갔다.
     * 제어기 기준이면 커버리지가 온전하다.</p>
     *
     * <p><b>다만 {@code pos} 는 제어 출력이지 상태 피드백이 아니다.</b>
     * {@code objectType=DO}, {@code pointDisplayName="퍼센트 제어"} 이고 게이트웨이가 주기
     * 수집하지 않는다({@code tbl_device_value_history} 에 제어기 이력 0건).
     * 그래서 <b>한 번도 제어된 적 없는 구역은 값이 빈 문자열</b>이고 여기서 NULL 이 된다
     * (실측 기준 활성 37구역 중 22구역이 NULL). 그 경우 서비스가 {@code openPercent=0} 으로
     * 내보낸다 — "닫힘"이 아니라 "모름"이라는 뜻이다.</p>
     *
     * <p>모터의 {@code pos-st}(실측)와 제어기의 {@code pos}(지령)는 <b>의미가 다른 값</b>이다.
     * 실제로 4층 전체 구역에서 실측 49 vs 지령 60 으로 갈린 적이 있다. 어느 쪽을 보여줄지는
     * 제품 판단이라 여기서 섞지 않는다. 둘 다 살리려면 지령 우선 + 실측 폴백
     * ({@code COALESCE}) 이 자연스럽다.</p>
     *
     * <p>{@code mode() WITHIN GROUP} 으로 묶여 있다. 제어기가 그룹당 1대, placement 1건이라
     * <b>입력이 늘 1행이어서 결과는 그 값 그대로</b>다 — 집계가 값을 바꾸지 않는다.
     * 모터 기준이던 시절의 형태가 남은 것이고, 제어기가 둘이 되는 경우에 대한 방어로도 읽힌다
     * (그때는 평균이 아니라 최빈값이어야 한다 — 제어 step 이 20 단위라 평균은
     * 되돌려 보낼 수 없는 값을 만든다).</p>
     *
     * <p>{@code valueRaw} 는 {@code "0.0"} 같은 문자열이고 빈 문자열·쓰레기 값이 섞인다.
     * 숫자 모양일 때만 캐스팅하고 아니면 NULL 로 떨어뜨린다.</p>
     *
     * <p>{@code placement} 를 {@code LEFT JOIN} 으로 둔 이유 — 제어기가 그룹당 1대뿐이라
     * INNER 면 배치가 빠진 제어기 하나 때문에 <b>그룹이 통째로 사라진다</b>(부분 손실이 아니다).
     * 지금은 37/37 배치돼 있지만 그 전제에 기대지 않는다.</p>
     *
     * <p>반환 컬럼: {@code [0]group_code · [1]group_name · [2]pos(numeric, null 가능)}</p>
     */
    @Query(value = """
            SELECT g.group_code,
                   g.group_name,
                   mode() WITHIN GROUP (ORDER BY pos.v) AS mode_pos
            FROM   tbl_device_group g
            JOIN   tbl_device_group_member m ON m.group_id  = g.group_id AND m.role = 'CONTROLLER'
            JOIN   tbl_device d              ON d.device_id = m.device_id AND d.active = true
            JOIN tbl_device_placement pl  ON pl.device_id = d.device_id
            JOIN tbl_unity_loc_zone z     ON z.zone_id   = pl.unity_zone_id
            JOIN tbl_unity_loc_floor f    ON f.floor_id  = z.floor_id
            LEFT   JOIN tbl_device_current_value cv ON cv.device_id = d.device_id
            LEFT   JOIN LATERAL (
                     SELECT CASE WHEN e->>'valueRaw' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                                 THEN (e->>'valueRaw')::numeric END AS v
                     FROM   jsonb_array_elements(COALESCE(cv.points, '[]'::jsonb)) AS e
                     WHERE  e->>'tagName' = 'pos'
                     LIMIT  1
                   ) pos ON true
            WHERE  g.group_type = 'ROLLSCREEN'
              AND  g.active = true
              -- AND  g.attributes->>'floor' = CAST(:floorName AS text)
              AND (CAST(:floorName AS text) IS NULL
                   OR f.floor_name = CAST(:floorName AS text)
                   OR CAST(f.floor_num AS text) = CAST(:floorName AS text))
            GROUP  BY g.group_id, g.group_code, g.group_name, g.sort_order
            ORDER  BY g.sort_order, g.group_code
            """, nativeQuery = true)
    List<Object[]> findGroupsByFloor(@Param("floorName") String floorName);

    /**
     * 제어 대상 — 그 구역 {@code CONTROLLER} 멤버의 {@code pos} 제어 포인트.
     *
     * <p>{@code group_code} 는 유일하므로 결과는 0건 아니면 1건이다.
     * {@code floorName} 을 같이 거는 건 층을 검증하기 위함이다 — HQS 가 보낸 층과 그룹의 층이
     * 다르면 제어하지 않는다(엉뚱한 층을 움직이는 것보다 거절이 낫다).</p>
     *
     * <p><b>현재 3층 19개 구역 중 {@code RS.F003.Z000}(전체) 하나만 command_point 가 있다.</b>
     * 나머지 18개는 제어 포인트가 등록되지 않아 0건이 나오고, 서비스가 그 사실을 메시지에 담는다.</p>
     *
     * <p>반환 컬럼: {@code [0]command_point_id · [1]device_name}</p>
     */
    @Query(value = """
            SELECT cp.command_point_id,
                   d.device_name
            FROM   tbl_device_group g
            JOIN   tbl_device_group_member m  ON m.group_id  = g.group_id AND m.role = 'CONTROLLER'
            JOIN   tbl_device d               ON d.device_id = m.device_id AND d.active = true
            JOIN   tbl_device_point p         ON p.device_id = d.device_id AND p.tag_name = 'pos'
            JOIN   tbl_device_command_point cp ON cp.point_id = p.point_id AND cp.active = true
            WHERE  g.group_type = 'ROLLSCREEN'
              AND  g.active = true
              AND  g.group_code = CAST(:groupCode AS text)
              AND  g.attributes->>'floor' = CAST(:floorName AS text)
            """, nativeQuery = true)
    Optional<Tuple> findControlPoint(@Param("groupCode") String groupCode,
                                     @Param("floorName") String floorName);

    /**
     * 그룹이 존재하는지(제어 실패 원인을 가르기 위한 것).
     *
     * <p>{@link #findControlPoint} 가 0건일 때 "그룹이 없다"와 "제어 포인트가 없다"는
     * 원인이 전혀 다르다. 전자는 요청이 잘못된 것이고 후자는 우리 데이터가 덜 찬 것이다.
     * 메시지를 가르려면 한 번 더 봐야 한다.</p>
     *
     * <p>반환 컬럼: {@code [0]group_code · [1]group_name · [2]floor}</p>
     */
    @Query(value = """
            SELECT g.group_code, g.group_name, g.attributes->>'floor'
            FROM   tbl_device_group g
            WHERE  g.group_type = 'ROLLSCREEN'
              AND  g.group_code = CAST(:groupCode AS text)
            """, nativeQuery = true)
    List<Object[]> findGroupByCode(@Param("groupCode") String groupCode);
}
