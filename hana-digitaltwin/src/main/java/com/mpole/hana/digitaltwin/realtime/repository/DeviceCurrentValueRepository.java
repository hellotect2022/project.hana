package com.mpole.hana.digitaltwin.realtime.repository;

import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * tbl_device_current_value 접근(디지털트윈 측).
 * <p>값(points)의 쓰기 주체는 hana-event(SnapshotService)다. 여기서는 v2.1 설계에 따라
 * <b>connection_state 컬럼만</b> 타깃 갱신한다 — 엔티티 save 금지(로드 스냅샷의 points 를
 * 통째로 되써서 1초 값 파이프라인과 경합함).</p>
 */
public interface DeviceCurrentValueRepository extends JpaRepository<DeviceCurrentValue, Long> {

    /**
     * 수동 통신상태 반영(infra 미소속 장비 전용 — MQTT 등). 행이 없으면 생성(points NULL 허용).
     * <p>updated_at 은 값 신선도(staleness) 의미라 기존 행에서는 건드리지 않는다.</p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO tbl_device_current_value (device_id, device_key, connection_state, created_at, updated_at)
            VALUES (:deviceId, :deviceKey, :state, NOW(), NOW())
            ON CONFLICT (device_id)
            DO UPDATE SET connection_state = :state
            """, nativeQuery = true)
    int upsertConnectionState(
            @Param("deviceId") Long deviceId,
            @Param("deviceKey") String deviceKey,
            @Param("state") String state
    );

    /**
     * 값(points) 타깃 upsert — <b>digitaltwin 이 단독 소유하는 장비 전용</b>(RabbitMQ/emblaze 부스 등).
     *
     * <p>클래스 주석의 "값 쓰기는 hana-event" 규칙은 <b>XN 값 파이프라인을 타는 장비</b> 이야기다.
     * 부스 재실은 emblaze RabbitMQ 로 digitaltwin 에 직접 들어오고 event 파이프라인에 존재하지 않으므로
     * 경합 대상이 아니다. 단 다음 두 가지를 반드시 지킨다.</p>
     * <ul>
     *   <li>엔티티 {@code save()} 를 쓰지 않는다 — 로드 스냅샷의 points 를 통째로 되쓴다.</li>
     *   <li>{@code connection_state} 는 건드리지 않는다 — event 소유 컬럼이다.</li>
     * </ul>
     *
     * <p>대상 장비를 gateway 골격에 편입시키지 않는 것도 전제다
     * ({@code tbl_device_point} 에 행을 만들지 않는다 — 만들면 기동 시 전량 발행에 휩쓸려
     * points 가 null 로 덮인다).</p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO tbl_device_current_value (device_id, device_key, points, created_at, updated_at)
            VALUES (:deviceId, :deviceKey, CAST(:points AS jsonb), NOW(), NOW())
            ON CONFLICT (device_id)
            DO UPDATE SET points = EXCLUDED.points,
                          updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertPoints(
            @Param("deviceId") Long deviceId,
            @Param("deviceKey") String deviceKey,
            @Param("points") String pointsJson
    );

    /**
     * <b>태그 단위 병합</b> upsert — 같은 tagName 원소만 교체하고 나머지 포인트는 보존한다.
     *
     * <p>{@link #upsertPoints} 는 배열을 통째로 교체하므로 한 장비가 포인트를 2개 이상 가지면
     * 서로를 지운다(부스: 재실 {@code occ} + 물리버튼). 이 메서드는 그 문제를 없앤다.</p>
     *
     * <pre>
     *   기존 points 에서 tagName 이 같은 원소를 제거  →  새 원소 1건을 append
     * </pre>
     *
     * <p>SQL 한 문장으로 처리해 <b>원자적</b>이다 — 읽기-수정-쓰기 왕복이 없고, 애플리케이션
     * 메모리 상태에 의존하지 않아 재기동 직후에도 다른 태그 값을 잃지 않는다.</p>
     *
     * @param point 단일 원소 JSON <b>배열</b> 문자열. 예: {@code [{"tagName":"occ","valueRaw":"1"}]}
     */
    @Modifying
    @Query(value = """
            INSERT INTO tbl_device_current_value (device_id, device_key, points, created_at, updated_at)
            VALUES (:deviceId, :deviceKey, CAST(:point AS jsonb), NOW(), NOW())
            ON CONFLICT (device_id)
            DO UPDATE SET
                points = COALESCE(
                    (SELECT jsonb_agg(e)
                     FROM jsonb_array_elements(
                             COALESCE(tbl_device_current_value.points, '[]'::jsonb)) AS e
                     WHERE e->>'tagName' IS DISTINCT FROM CAST(:tagName AS text)),
                    '[]'::jsonb
                ) || EXCLUDED.points,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertPointByTag(
            @Param("deviceId") Long deviceId,
            @Param("deviceKey") String deviceKey,
            @Param("tagName") String tagName,
            @Param("point") String pointJsonArray
    );
}
