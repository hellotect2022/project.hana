package com.mpole.hana.digitaltwin.external.hqs.wc;

import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 부스 재실 현황 — <b>장비 그룹 기준</b>. HQS {@code /boothStatus} 응답 원본.
 *
 * <p>구현을 바꾸기 전에는 {@code DeviceWcBoothRepository.findBoothOccupancy} 가 같은 일을
 * zone 계층으로 했다. 8개 필터 조합으로 대조해 72/72 행이 일치하는 것을 확인한 뒤 그쪽을
 * 걷어냈다. 바뀐 것은 "부스를 무엇으로 아는가" 하나다.</p>
 *
 * <pre>
 *   이전  부스 = zone(depth 1), 화장실 = 그 부모 zone(zone_parent_id)
 *   현재  부스 = tbl_device_group(group_type='WC_BOOTH'), 화장실 = attributes.wcCode
 * </pre>
 *
 * <h3>왜 바꿨나</h3>
 * <p>zone 은 3D 콜라이더가 정본이라, 부스를 zone 으로 표현하면 Unity 가 영역을 그려줄
 * 때까지 배치 작업이 멈춘다. 실제로 부스 73개가 전부 {@code zone_mesh_name='there_is_no_data'}
 * 더미였다. 그룹은 도면과 무관하므로 그 결합이 끊긴다.</p>
 *
 * <h3>zone 을 완전히 버리지는 않는다</h3>
 * <p>{@code florId} 는 여전히 {@code placement → zone → floor} 로 구한다. 층은
 * "장비가 어디 있나"라서 배치가 정본이다. 떼어내려는 것은 zone 의 <b>계층</b>
 * ({@code zone_parent_id})이지 배치가 아니다.</p>
 *
 * <h3>부스당 1행을 만드는 방법</h3>
 * <p>이전 구현은 {@code category_code='OCC'} 로 기준 장비를 하나 골라 1행을 만들었다.
 * 지금은 <b>그룹으로 묶고 집계</b>한다 — 한 부스의 {@code MONITOR} 멤버(재실센서·버튼)를
 * 모아 {@code MAX(occ)} 를 취한다. 버튼은 {@code occ} 태그가 없어 {@code '0'} 으로 떨어지므로
 * 결과에 영향이 없고, 센서가 둘 이상 붙어도 "하나라도 재실이면 재실"이 된다.
 * 카테고리 코드를 쿼리에 박지 않는 것이 요점이다.</p>
 *
 * <h3>장비 0대 그룹은 나오지 않는다</h3>
 * <p>멤버를 {@code INNER JOIN} 한다. {@code WC_1F_M01_B01} 처럼 짝이 될 장비가 아직 없는
 * 부스는 이전 구현에서도 안 나왔으므로 그대로 맞춘다(73 그룹 중 72행).</p>
 *
 * <h3>재실 판정 — 이전과 동일</h3>
 * <p>PIR 이 증가할 때마다 {@code occ="1"} 을 덮어쓰므로 그 포인트의 {@code updateDatetime} 이
 * 마지막 움직임 시각이고, {@code now - updateDatetime > vacancySeconds} 면 퇴실로 본다.
 * 스케줄러 없이 조회 시점에 계산한다.</p>
 *
 * <p>반환 컬럼: {@code [0]wc_id · [1]gender · [2]booth_id · [3]occ}</p>
 */
@Repository
public interface HqsBoothRepository extends JpaRepository<DeviceGroup, Long> {

    /**
     * @param florId         층 이름({@code 4F}) 또는 층 번호({@code 4}). null 이면 전 층
     * @param wcId           화장실 코드({@code WC_4F_M01}). null 이면 전체
     * @param gender         {@code M} 또는 {@code F}/{@code W}. null 이면 전체
     * @param vacancySeconds 마지막 움직임 이후 이 시간이 지나면 퇴실. 0 이하면 시간 검사 없음
     */
    @Query(value = """
            SELECT g.attributes->>'wcCode' AS wc_id,
                   CASE WHEN COALESCE(g.attributes->>'gender',
                                      left(split_part(g.attributes->>'wcCode', '_', 3), 1)) = 'M'
                        THEN 'M' ELSE 'F' END AS gender,
                   g.group_code AS booth_id,
                   MAX(COALESCE(
                     (SELECT CASE
                               WHEN e->>'valueRaw' <> '1' THEN '0'
                               WHEN e->>'updateDatetime' IS NULL THEN '0'
                               WHEN CAST(:vacancySeconds AS int) <= 0 THEN '1'
                               WHEN (e->>'updateDatetime')::timestamptz
                                    > now() - (CAST(:vacancySeconds AS int) * interval '1 second')
                                    THEN '1'
                               ELSE '0'
                             END
                        FROM jsonb_array_elements(COALESCE(cv.points, '[]'::jsonb)) AS e
                       WHERE e->>'tagName' = 'occ'
                       LIMIT 1), '0')) AS occ
            FROM tbl_device_group g
            JOIN tbl_device_group_member m ON m.group_id = g.group_id
            JOIN tbl_device d             ON d.device_id = m.device_id
            JOIN tbl_device_placement pl  ON pl.device_id = d.device_id
            JOIN tbl_unity_loc_zone z     ON z.zone_id   = pl.unity_zone_id
            JOIN tbl_unity_loc_floor f    ON f.floor_id  = z.floor_id
            LEFT JOIN tbl_device_current_value cv ON cv.device_id = d.device_id
            WHERE g.group_type = 'WC_BOOTH'
              AND g.active = true
              AND m.role = 'MONITOR'
              AND d.active = true
              AND (CAST(:florId AS text) IS NULL
                   OR f.floor_name = CAST(:florId AS text)
                   OR CAST(f.floor_num AS text) = CAST(:florId AS text))
              AND (CAST(:wcId AS text) IS NULL
                   OR g.attributes->>'wcCode' = CAST(:wcId AS text))
              AND (CAST(:gender AS text) IS NULL
                   OR CASE WHEN COALESCE(g.attributes->>'gender',
                                         left(split_part(g.attributes->>'wcCode', '_', 3), 1)) = 'M'
                           THEN 'M' ELSE 'F' END
                      = CASE WHEN upper(CAST(:gender AS text)) = 'M' THEN 'M' ELSE 'F' END)
            GROUP BY g.group_id, g.group_code, g.attributes
            ORDER BY g.attributes->>'wcCode', g.group_code
            """, nativeQuery = true)
    List<Object[]> findBoothOccupancyByGroup(@Param("florId") String florId,
                                             @Param("wcId") String wcId,
                                             @Param("gender") String gender,
                                             @Param("vacancySeconds") int vacancySeconds);
}
