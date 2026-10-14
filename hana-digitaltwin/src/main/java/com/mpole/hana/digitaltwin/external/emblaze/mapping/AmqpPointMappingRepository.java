package com.mpole.hana.digitaltwin.external.emblaze.mapping;

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
     * RabbitMQ 로 채워지는 포인트 전량 + <b>그 장비가 속한 부스의 신원</b>.
     *
     * <p>기동 시 한 번만 돈다. 여기서 실어 두는 값들이 곧 HQS 로 나갈 식별자라,
     * <b>버튼이 눌릴 때마다 DB 를 치지 않아도 된다</b>.</p>
     *
     * <p>반환 컬럼 순서:
     * {@code [0]ref_device_code · [1]ref_point_code · [2]tag_name · [3]device_id · [4]device_key ·
     * [5]group_code · [6]wc_code · [7]gender · [8]floor_name}</p>
     *
     * <p>{@code ref_point_code} 가 비어 있는 포인트는 제외한다 — 벤더가 보내주는 필드가 아니라
     * 우리가 계산해 넣는 파생값({@code occ})이라 라우팅 대상이 아니다.</p>
     *
     * <h3>조인을 전부 LEFT 로 두는 이유</h3>
     * <p>부스 그룹·배치가 빠진 장비라도 <b>현재값 기록은 계속돼야 한다</b>. INNER 로 묶으면
     * 그룹에 안 들어간 장비의 값이 조용히 사라진다 — 매핑은 라우팅이 본업이고,
     * 부스 신원은 버튼 전송에만 쓰이는 부가 정보다.</p>
     *
     * <p>현재 AMQP 포인트 2250건이 전부 부스 그룹에 속하고, 한 장비가 두 그룹에 걸친 경우가
     * 없어 행이 늘지 않는다(2250 → 2250). 그래도 그 전제에 기대지 않는다.</p>
     *
     * <h3>gender</h3>
     * <p>{@code attributes.gender} 가 있으면 그것을, 없으면 화장실 코드 세 번째 마디의 첫 글자로
     * 판정한다({@code WC_4F_M01} → {@code M}). <b>조회 API(boothStatus)의 SQL 과 같은 식</b>이라
     * 푸시와 조회가 같은 값을 낸다.</p>
     */
    @Query(value = """
            SELECT LOWER(p.ref_device_code) AS ref_device_code,
                   p.ref_point_code,
                   p.tag_name,
                   d.device_id,
                   d.device_key,
                   g.group_code,
                   g.attributes->>'wcCode' AS wc_code,
                   CASE WHEN COALESCE(g.attributes->>'gender',
                                      left(split_part(g.attributes->>'wcCode', '_', 3), 1)) = 'M'
                        THEN 'M' ELSE 'F' END AS gender,
                   f.floor_name
            FROM tbl_device_point p
            JOIN tbl_device d           ON d.device_id   = p.device_id
            LEFT JOIN tbl_device_group_member m ON m.device_id = d.device_id
            LEFT JOIN tbl_device_group   g  ON g.group_id   = m.group_id
                                           AND g.group_type = 'WC_BOOTH'
            LEFT JOIN tbl_device_placement pl ON pl.device_id = d.device_id
            LEFT JOIN tbl_unity_loc_zone   z  ON z.zone_id    = pl.unity_zone_id
            LEFT JOIN tbl_unity_loc_floor  f  ON f.floor_id   = z.floor_id
            WHERE p.data_source = 'AMQP'
              AND p.active = true
              AND d.active = true
              AND COALESCE(p.ref_device_code, '') <> ''
              AND COALESCE(p.ref_point_code, '') <> ''
            """, nativeQuery = true)
    List<Object[]> findAmqpPointMappings();

    /**
     * AMQP 포인트의 표시·식별 정보 전량 — 현재값 JSONB 에 같이 실어 보낼 값들.
     *
     * <p>{@link #findAmqpPointMappings()} 와 <b>대상이 조금 다르다.</b> 그쪽은 벤더 필드명으로
     * 라우팅할 대상을 찾는 것이라 {@code ref_point_code} 가 있는 것만 본다. 여기는 우리가 쓰는
     * 모든 AMQP 포인트가 대상이라 그 조건이 없다 — {@code occ} 처럼 <b>벤더가 보내주지 않고
     * 우리가 계산해 넣는 파생값</b>도 화면에는 이름이 떠야 하기 때문이다.</p>
     *
     * <pre>
     *   findAmqpPointMappings  ref_point_code 있는 것만   2250건   (uuid, 필드) → 어디에 쓸지
     *   findPointMeta          AMQP 포인트 전부          2632건   무엇을 같이 적을지
     * </pre>
     *
     * <p>반환 컬럼 순서:
     * {@code [0]device_id · [1]tag_name · [2]point_id · [3]point_key · [4]point_display_name ·
     * [5]unit · [6]point_type · [7]ref_device_code · [8]ref_point_code}</p>
     *
     * <p>{@code (device_id, tag_name)} 은 중복이 없어 그대로 캐시 키로 쓴다.</p>
     */
    @Query(value = """
            SELECT p.device_id,
                   p.tag_name,
                   p.point_id,
                   p.point_key,
                   p.point_display_name,
                   p.unit,
                   p.point_type,
                   p.ref_device_code,
                   p.ref_point_code
            FROM tbl_device_point p
            JOIN tbl_device d ON d.device_id = p.device_id
            WHERE p.data_source = 'AMQP'
              AND p.active = true
              AND d.active = true
            """, nativeQuery = true)
    List<Object[]> findPointMeta();
}
