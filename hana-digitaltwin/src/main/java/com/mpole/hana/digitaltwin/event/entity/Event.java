package com.mpole.hana.digitaltwin.event.entity;

import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 이벤트 발생 1건({@code tbl_event}) — <b>실제로 일어난 것</b>의 불변 기록 + 상태 2축.
 *
 * <p>3계층(설정 → 발생 → 발행) 중 <b>발생</b>을 담당한다. 무엇을 감시할지는 {@link EventRule},
 * 이 건에서 무엇을 발행했는지는 {@link EventAction} 이다.</p>
 *
 * <h3>관계</h3>
 * <pre>
 *   EventRule / EventRuleLevel ──(발생 순간 값 복사)──▶ ┌───────────────┐
 *   tbl_device      ◀── device_id (소프트 참조) ─────── │ Event(이 클래스)│
 *   tbl_device_point◀── point_id  (소프트 참조) ─────── └───────┬───────┘
 *                                                              │ 1 : N (소프트 참조, FK 없음)
 *                                                      ┌───────▼───────┐
 *                                                      │ EventAction   │ 채널마다 1행
 *                                                      └───────────────┘
 * </pre>
 *
 * <h3>왜 값을 복사(스냅샷)해 두는가</h3>
 * {@code rule_name}·{@code level}·{@code threshold_value}·{@code outputs} 는 규칙에서 읽어와 그대로
 * 박아 둔다. 나중에 규칙을 수정하거나 삭제해도 <b>과거 이벤트가 "그때 무슨 근거로 떴는지"</b>는
 * 변하지 않아야 하기 때문이다. 그래서 규칙/장비/포인트는 전부 FK 없는 소프트 참조다.
 *
 * <h3>상태 2축 (ISA-18.2 모델)</h3>
 * <pre>
 *                     acked_at = null        acked_at 있음
 *   active = true     ACTIVE_UNACK           ACTIVE_ACK
 *   active = false    CLEARED_UNACK          CLOSED
 * </pre>
 * 두 축은 <b>서로 독립</b>이다 — 값이 정상 복귀해도 아무도 확인하지 않았을 수 있고(CLEARED_UNACK),
 * 확인했어도 조건은 계속 성립할 수 있다(ACTIVE_ACK). 조합은 {@link #getStatus()} 가 계산한다.
 *
 * <h3>생명주기</h3>
 * <pre>
 *   조건 성립 & 활성 없음 → INSERT(active=true) + 액션 발행 + 웹소켓 EVENT
 *   조건 성립 & 활성 있음 → 무시                      ← 중복 억제(cooldown 불필요)
 *   조건 해소 & 활성 있음 → active=false, cleared_*   + 웹소켓 EVENT_CLEAR
 *   사람이 확인          → acked_at / acked_by       + POPUP 액션 DONE
 *   2개월 경과 & !active → core.purge_events() 로 삭제 (활성 건은 오래돼도 남긴다)
 * </pre>
 *
 * <h3>쓰기 주체</h3>
 * 스키마는 digitaltwin(JPA, ddl-auto=update)이 소유하지만 <b>발생 INSERT·해소 UPDATE 는
 * event-server(WebFlux/R2DBC)</b>가 한다. digitaltwin 이 쓰는 것은 사람이 만드는 확인(ack)뿐이다.
 * 그래서 event-server 가 문자열로 넣는 값(level/scope/operator/conditionKind/pointType)은
 * enum 매핑을 강제하지 않고 String 으로 받는다.
 */
@Entity
@Table(
        name = "tbl_event",
        indexes = {
                @Index(name = "idx_event_occurred", columnList = "occurred_at"),
                @Index(name = "idx_event_level", columnList = "level"),
                @Index(name = "idx_event_device", columnList = "device_id")
        }
)
@Comment("이벤트 발생 이력 — 규칙에 걸려 실제로 일어난 한 건(3계층 중 '발생')")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event extends DateEntity {

    /**
     * UUIDv7 — 발생 시점에 event-server 가 생성한다(DB 생성 아님).
     * <p>웹소켓으로 나가는 식별자와 동일해서, 클라이언트가 받은 id 로 그대로 확인(ack)·완료 보고를 한다.
     * v7 은 상위 비트가 밀리초 타임스탬프라 인덱스 지역성이 좋고 발생순 정렬이 된다.</p>
     */
    @Id
    @Column(name = "event_id")
    @Comment("이벤트 PK(UUIDv7). 발생 시점에 서버가 생성하며 웹소켓 식별자와 같은 값이라 INSERT 응답을 기다리지 않고 발행할 수 있다")
    private UUID eventId;

    @Column(name = "rule_id")
    @Comment("발생시킨 규칙 id(소프트 참조). 규칙이 지워져도 이력은 남는다")
    private Long ruleId;

    @Column(name = "rule_name")
    @Comment("발생 시점의 규칙명 스냅샷. 나중에 규칙명을 바꿔도 과거 이벤트 표시는 그대로다")
    private String ruleName;

    @Column(name = "level")
    @Comment("발생 심각도(CAUTION/WARNING/CRITICAL). 여러 등급이 성립했을 때 채택된 최고 심각도")
    private String level;

    @Column(name = "scope")
    @Comment("규칙 적용 범위 스냅샷(CATEGORY/DEVICE)")
    private String scope;

    @Column(name = "device_id")
    @Comment("발생 장비 id(소프트 참조). CCTV 조립·화면 이동의 기준")
    private Long deviceId;

    @Column(name = "device_key")
    @Comment("발생 장비 식별 키 스냅샷(외부 연동용 문자열)")
    private String deviceKey;

    @Column(name = "point_id")
    @Comment("발생 포인트 id(소프트 참조). 활성 유니크 인덱스의 구성 요소이며 NULL 이면 DB 중복 차단이 걸리지 않는다")
    private Long pointId;

    @Column(name = "tag_name")
    @Comment("발생 포인트 태그명 스냅샷")
    private String tagName;

    @Column(name = "point_type")
    @Comment("포인트 타입 스냅샷(AI/DI 등)")
    private String pointType;

    @Column(name = "condition_kind")
    @Comment("판정 방식 스냅샷(THRESHOLD/TOGGLE)")
    private String conditionKind;

    @Column(name = "value_raw")
    @Comment("발생 순간의 포인트 원시값. '왜 떴는지'의 근거")
    private String valueRaw;

    @Column(name = "threshold_value")
    @Comment("채택된 등급의 임계값 스냅샷(THRESHOLD 전용). value_raw 와 함께 보면 판정 근거가 완성된다")
    private Double thresholdValue;

    @Column(name = "operator")
    @Comment("비교 연산자 스냅샷(GTE/LTE/GT/LT)")
    private String operator;

    @Column(name = "trigger_value")
    @Comment("TOGGLE 트리거값 스냅샷")
    private String triggerValue;

    /**
     * 발생 레벨의 {@code outputs} 를 그대로 복사한 값. 조립 API 가 이 값으로 SOP/CCTV 표출을 결정하고,
     * 켜진 채널 수만큼 {@link EventAction} 행이 만들어진다. 비어 있으면 표시 전용 이벤트다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outputs", columnDefinition = "jsonb")
    @Comment("발생 레벨의 출력 채널 세트 스냅샷. 켜진 채널 수만큼 tbl_event_action 행이 생기고, 조립 API 가 이 값으로 SOP/CCTV 표출을 결정한다")
    private OutputSpec outputs;

    @Column(name = "icon")
    @Comment("발생 등급의 아이콘 키 스냅샷(클라이언트 렌더용)")
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect")
    @Comment("발생 등급의 3D 이펙트 스냅샷(클라이언트 렌더용)")
    private EventEffect effect;

    @Column(name = "occurred_at")
    @Comment("발생 시각. 목록 정렬과 2개월 보관 정책의 기준")
    private OffsetDateTime occurredAt;

    /* ─────────────────────────────────────────────────────────────
     * 상태 2축 — 서로 독립이다(ISA-18.2 모델).
     *   active : 조건이 지금도 성립 중인가        (시스템 판단)
     *   acked  : 운전원이 확인했는가              (사람 판단)
     * 값이 정상 복귀해도 확인되지 않을 수 있고, 확인했어도 조건은 계속 성립할 수 있다.
     * ───────────────────────────────────────────────────────────── */

    /**
     * 조건 성립 중 여부. 부분 유니크 인덱스
     * {@code UNIQUE(rule_id, device_id, point_id) WHERE active} 가 걸려 있어
     * 같은 규칙+포인트의 활성 이벤트는 DB 레벨에서 1건만 존재한다(중복 발생 차단).
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    @Comment("[Active 축·시스템] 조건이 지금도 성립 중인가. UNIQUE(rule_id,device_id,point_id) WHERE active 로 같은 규칙+포인트의 활성 건은 1개만 존재한다(중복 발생 차단). 활성 건은 보관 기간이 지나도 삭제하지 않는다")
    private boolean active = true;

    /** 정상 복귀(해소) 시각. null 이면 아직 해소되지 않음. */
    @Column(name = "cleared_at")
    @Comment("[Active 축] 정상 복귀(해소) 시각. null 이면 아직 해소되지 않았다. 해소 시 웹소켓으로 EVENT_CLEAR 가 나간다")
    private OffsetDateTime clearedAt;

    /** 해소 시점의 포인트 값. */
    @Column(name = "cleared_value")
    @Comment("[Active 축] 해소 시점의 포인트 값. deadband 를 넘어 정상 범위로 돌아온 값이 기록된다")
    private String clearedValue;

    /** 운전원 확인 시각. null 이면 미확인. */
    @Column(name = "acked_at")
    @Comment("[Ack 축·사람] 운전원 확인 시각. active 와 독립이라 해소 여부와 무관하게 기록된다. 최초 확인만 남고 재호출은 무시")
    private OffsetDateTime ackedAt;

    @Column(name = "acked_by")
    @Comment("[Ack 축] 확인한 사용자 id(tbl_user.user_id 소프트 참조)")
    private Long ackedBy;

    /** 조회 편의용 파생 상태(컬럼 아님) — 두 축의 조합. */
    @Transient
    public EventStatus getStatus() {
        if (active) {
            return ackedAt == null ? EventStatus.ACTIVE_UNACK : EventStatus.ACTIVE_ACK;
        }
        return ackedAt == null ? EventStatus.CLEARED_UNACK : EventStatus.CLOSED;
    }
}
