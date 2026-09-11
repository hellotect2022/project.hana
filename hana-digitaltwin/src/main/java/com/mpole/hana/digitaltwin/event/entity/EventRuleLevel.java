package com.mpole.hana.digitaltwin.event.entity;

import com.mpole.hana.common.dto.OutputSpec;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 이벤트 규칙의 심각도 등급({@code tbl_event_rule_level}) — <b>얼마나 심각하면 무엇을 켤지</b>.
 *
 * <p>한 규칙이 등급을 여러 개 가진다. THRESHOLD 조건이면 등급마다 임계값이 달라 값이 얼마나
 * 나빠졌는지로 등급이 갈리고(만족하는 것 중 <b>최고 심각도</b> 선택), TOGGLE 조건이면 임계값 개념이
 * 없어 등급 하나만 두는 것이 보통이다.</p>
 *
 * <h3>관계</h3>
 * <pre>
 *   EventRule ──1 : N──▶ EventRuleLevel (이 클래스)
 *                              │
 *                              │ outputs 를 발생 순간 그대로 복사(스냅샷)
 *                              ▼
 *                          Event.outputs ──▶ 켜진 채널 수만큼 EventAction 행 생성
 * </pre>
 *
 * <h3>예시</h3>
 * <pre>
 *   규칙 "고온경보"  tag=TEMP, condition=THRESHOLD, operator=GTE
 *     ├ level=CAUTION  threshold=10  outputs={popup}                → 값 10.5 면 여기까지 성립
 *     ├ level=WARNING  threshold=11  outputs={popup, sop}
 *     └ level=CRITICAL threshold=12  outputs={popup, sop, cctv}     → 값 12.5 면 셋 다 성립 → CRITICAL 채택
 * </pre>
 */
@Entity
@Table(name = "tbl_event_rule_level")
@Comment("이벤트 규칙의 심각도 등급 — 등급별 임계값과 켤 출력 채널")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRuleLevel extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "level_id")
    @Comment("등급 PK")
    private Long levelId;

    /**
     * FK 이름을 DB 와 동일하게 못 박는다({@code fk_event_rule_level_rule}, 마이그레이션 SQL 에서 생성).
     * 이름을 비워두면 ddl-auto 가 기존 FK 를 못 알아보고 임의 이름(FKxxxx)으로 하나 더 만든다 —
     * 카테고리 삭제 때 겪은 "FK 두 개" 문제와 같은 함정이다. 실제 ON DELETE CASCADE 는 DB 쪽 정의다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", foreignKey = @ForeignKey(name = "fk_event_rule_level_rule"))
    @Comment("소속 규칙(tbl_event_rule). 진짜 FK 이며 ON DELETE CASCADE — 규칙을 지우면 등급도 함께 사라진다")
    private EventRule rule;

    @Enumerated(EnumType.STRING)
    @Comment("심각도 등급. CAUTION(주의) < WARNING(경계) < CRITICAL(심각). 여러 등급이 성립하면 최고 심각도를 채택한다")
    private EventLevel level;

    @Column(name = "threshold_value")
    @Comment("이 등급의 임계값(THRESHOLD 조건 전용). 규칙의 operator 로 포인트 값과 비교한다. TOGGLE 이면 null")
    private Double thresholdValue;

    @Comment("이 등급에서 표시할 아이콘 키(클라이언트 렌더용). 평가에 쓰이지 않는다")
    private String icon;

    @Enumerated(EnumType.STRING)
    @Comment("이 등급에서 재생할 3D 이펙트(NONE/BLINK/COLOR_CHANGE/PULSE). 클라이언트 렌더용")
    private EventEffect effect;

    @Column(name = "display_order")
    @Builder.Default
    @Comment("화면 정렬 순서. 심각도 판정과는 무관하다(판정은 level 값으로 한다)")
    private int displayOrder = 0;

    /**
     * 이 등급이 발생할 때 켤 채널 세트. <b>발생 순간 {@code tbl_event.outputs} 로 그대로 복사</b>되므로,
     * 나중에 규칙을 바꿔도 과거 이벤트가 "그때 무엇을 켰는지"는 변하지 않는다.
     * null 이거나 켜진 채널이 하나도 없으면 액션이 생성되지 않는 <b>표시 전용 이벤트</b>가 된다(정상 케이스).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outputs", columnDefinition = "jsonb")
    @Comment("이 등급 발생 시 켤 출력 채널 세트(popup/sop/cctv/sms/email/deviceControl). 발생 시 tbl_event.outputs 로 스냅샷 복사되고, 켜진 채널 수만큼 tbl_event_action 행이 생긴다")
    private OutputSpec outputs;
}
