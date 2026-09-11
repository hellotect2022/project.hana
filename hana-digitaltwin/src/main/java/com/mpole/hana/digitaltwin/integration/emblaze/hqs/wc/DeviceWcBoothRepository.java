package com.mpole.hana.digitaltwin.integration.emblaze.hqs.wc;

import com.mpole.hana.digitaltwin.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceWcBoothRepository extends JpaRepository<Device, Long> {

    /**
     * 부스 재실 현황 조회 — HQS {@code boothStatus} 응답 원본.
     *
     * <h3>기준 장비</h3>
     * <p>부스 1칸에는 장비가 3대(조명·재실센서·버튼) 걸린다. 재실 현황이므로
     * <b>재실센서({@code OCC}) 장비만</b> 잡는다 — 그러면 부스당 정확히 1행이 나온다.</p>
     *
     * <h3>식별자</h3>
     * <pre>
     *   wcId      = 화장실 구역의 zone_code   (WC_4F_M01)
     *   wcBoothId = 부스 구역의 zone_code     (WC_4F_M01_B01)
     * </pre>
     * <p>device_id 를 쓰지 않는 이유 — 장비를 지웠다 다시 만들면 값이 바뀌어 HQS 쪽 데이터가
     * 끊긴다. {@code zone_code} 는 그 목적으로 만든 안정 식별자다.</p>
     *
     * <h3>재실 판정 — 스케줄러 없이 조회 시점에 계산한다</h3>
     * <p>재실은 결국 <b>"최근에 움직임이 있었나"</b> 하나다. PIR 이 증가할 때마다
     * {@code occ="1"} 을 덮어쓰므로 그 포인트의 {@code updateDatetime} 이 곧 <b>마지막 움직임 시각</b>이고,
     * {@code now - updateDatetime > vacancySeconds} 면 퇴실로 본다.</p>
     *
     * <p>예전에는 {@code @Scheduled} 스레드가 메모리의 마지막 움직임 시각을 훑어 {@code occ="0"} 을
     * 써 넣었다. 그 방식은 <b>서버를 재기동하면 상태를 잊어</b> 재실로 찍힌 부스가 영원히
     * 사용중으로 남았고(복구 경로 없음), sweep 주기(5초)만큼 판정이 늦었다.
     * 계산으로 바꾸면 두 문제가 같이 사라진다 — 상태가 DB 의 타임스탬프 하나뿐이다.</p>
     *
     * <p>센서가 죽어 메시지가 끊긴 경우도 같은 식으로 걸린다(타임스탬프가 안 늘어남).
     * 별도의 신선도 검사가 필요 없다.</p>
     *
     * <h3>성별</h3>
     * <p>{@code attributes->>'gender'} 가 있으면 그것을 쓰고, 없으면 zone_code 세 번째 마디의
     * 첫 글자(M/W)로 판정한다. 외부로는 {@code M}/{@code F} 로 내보낸다.</p>
     *
     * <p>반환 컬럼 순서:
     * {@code [0]wc_id · [1]wc_name · [2]gender · [3]booth_id · [4]booth_name · [5]occ}</p>
     *
     * <h3>층 식별자</h3>
     * <p>{@code florId} 는 {@code floor_name}({@code 4F}·{@code B2F})과
     * {@code floor_num}({@code 4}·{@code -2}) <b>둘 다</b> 받는다 — HQS 가 어느 형식으로 보낼지
     * 확정되지 않았고, 한쪽만 받으면 형식이 어긋나는 순간 <b>조용히 빈 배열</b>이 나가기 때문이다.</p>
     *
     * @param florId 층 이름 또는 층 번호. null 이면 전 층
     * @param wcId   화장실 zone_code. null 이면 전체
     * @param gender         {@code M} 또는 {@code F}/{@code W}. null 이면 전체
     * @param vacancySeconds 마지막 움직임 이후 이 시간이 지나면 퇴실로 본다. 0 이하면 시간 검사 없음
     */
    @Query(value = """
            SELECT pz.zone_code AS wc_id,
                   pz.zone_name AS wc_name,
                   CASE WHEN COALESCE(pz.attributes->>'gender',
                                      left(split_part(pz.zone_code, '_', 3), 1)) = 'M'
                        THEN 'M' ELSE 'F' END AS gender,
                   z.zone_code AS booth_id,
                   z.zone_name AS booth_name,
                   COALESCE(
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
                       LIMIT 1), '0') AS occ
            FROM tbl_device d
            JOIN tbl_device_category c    ON c.category_id = d.category_id
            JOIN tbl_device_placement pl  ON pl.device_id  = d.device_id
            JOIN tbl_unity_loc_zone z     ON z.zone_id     = pl.unity_zone_id
            JOIN tbl_unity_loc_zone pz    ON pz.zone_id    = z.zone_parent_id
            JOIN tbl_unity_loc_floor f    ON f.floor_id    = z.floor_id
            LEFT JOIN tbl_device_current_value cv ON cv.device_id = d.device_id
            WHERE c.category_code = 'OCC'
              AND d.active = true
              AND (CAST(:florId AS text) IS NULL
                   OR f.floor_name = CAST(:florId AS text)
                   OR CAST(f.floor_num AS text) = CAST(:florId AS text))
              AND (CAST(:wcId   AS text) IS NULL OR pz.zone_code  = CAST(:wcId AS text))
              AND (CAST(:gender AS text) IS NULL
                   OR CASE WHEN COALESCE(pz.attributes->>'gender',
                                         left(split_part(pz.zone_code, '_', 3), 1)) = 'M'
                           THEN 'M' ELSE 'F' END
                      = CASE WHEN upper(CAST(:gender AS text)) = 'M' THEN 'M' ELSE 'F' END)
            ORDER BY pz.zone_code, z.zone_code
            """, nativeQuery = true)
    List<Object[]> findBoothOccupancy(@Param("florId") String florId,
                                      @Param("wcId") String wcId,
                                      @Param("gender") String gender,
                                      @Param("vacancySeconds") int vacancySeconds);

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

    /** 통계 장비 조회 — 파생 집계값을 기록할 대상. */
    Optional<Device> findFirstByDeviceKey(String deviceKey);

}
