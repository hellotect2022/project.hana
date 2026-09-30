package com.mpole.hana.digitaltwin.event.entity;

import com.mpole.hana.common.constant.SuppressMode;
import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.util.ArrayList;
import java.util.List;

/**
 * 이벤트 규칙({@code tbl_event_rule}) — <b>무엇을 감시하고, 걸리면 어떤 채널을 켤지</b>의 설정.
 *
 * <p>3계층(설정 → 발생 → 발행) 중 <b>설정</b>을 담당한다. 실제 발생 기록은 {@link Event},
 * 채널별 발행은 {@link EventAction} 이다. 전체 구조는
 * {@link com.mpole.hana.digitaltwin.event.entity 패키지 설명} 참고.</p>
 *
 * <h3>관계</h3>
 * <pre>
 *   tbl_device_category ◀── category_id ─┐   (scope=CATEGORY, 소프트 참조)
 *   tbl_device          ◀── device_id ───┤   (scope=DEVICE,   소프트 참조)
 *                                        │
 *                              ┌─────────┴──────────┐
 *                              │ EventRule (이 클래스)│
 *                              └─────────┬──────────┘
 *                                        │ 1 : N  (진짜 FK, ON DELETE CASCADE)
 *                              ┌─────────▼──────────┐
 *                              │ EventRuleLevel     │ 등급별 임계값 + 켤 채널
 *                              └────────────────────┘
 * </pre>
 *
 * <h3>매칭 규칙</h3>
 * 한 포인트에 대해 <b>개별 장비(DEVICE) 규칙이 있으면 그것만</b> 적용하고, 없을 때만 그 장비가 속한
 * 소분류의 CATEGORY 규칙을 상속한다. 즉 장비별 규칙이 카테고리 규칙을 override 한다.
 * CATEGORY 규칙의 {@code category_id} 는 대/중/소 어느 깊이여도 되며, 평가 시 하위 소분류 전체로 전개된다.
 *
 * <h3>중복 발생을 막는 방식</h3>
 * 시간 기반 cooldown 이 아니라 <b>Active 축</b>이다 — 같은 규칙+포인트에 활성 이벤트가 있으면 새로
 * 발생시키지 않는다(DB 부분 유니크 인덱스가 보증). 그래서 시간 기반 쿨다운 설정이 없다(2026-09-30 삭제).
 */
@Entity
@Table(name = "tbl_event_rule")
@Comment("이벤트 규칙 — 감시 대상·조건·출력 채널 설정(3계층 중 '설정')")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRule extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    @Comment("규칙 PK. 발생 이벤트(tbl_event.rule_id)가 소프트 참조로 이 값을 박제한다")
    private Long ruleId;

    @Column(name = "rule_name", nullable = false)
    @Comment("규칙명. 발생 시 tbl_event.rule_name 으로 복사되어, 나중에 규칙명을 바꿔도 과거 이벤트 표시는 그대로 유지된다")
    private String ruleName;

    @Column(columnDefinition = "TEXT")
    @Comment("규칙 설명(운영 메모). 평가에 쓰이지 않는다")
    private String description;

    /** {@link RuleScope#CATEGORY} 면 {@link #categoryId}, {@link RuleScope#DEVICE} 면 {@link #deviceId} 를 본다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Comment("적용 범위. CATEGORY=category_id 하위 장비 전체 / DEVICE=device_id 단일 장비")
    private RuleScope scope;

    @Column(name = "category_id")
    @Comment("scope=CATEGORY 일 때 대상 카테고리(소프트 참조). 대/중/소 어느 깊이든 가능하며 평가 시 하위 소분류로 전개된다")
    private Long categoryId;

    @Column(name = "device_id")
    @Comment("scope=DEVICE 일 때 대상 장비(소프트 참조). 이 규칙이 같은 태그의 카테고리 규칙을 override 한다")
    private Long deviceId;

    @Column(name = "tag_name")
    @Comment("감시 대상 포인트의 태그명. 이 값이 비면 어떤 포인트와도 매칭되지 않아 규칙이 무시된다")
    private String tagName;

    @Column(name = "point_type")
    @Comment("포인트 타입 방어값(AI/DI 등). 값이 있고 실제 포인트 타입과 다르면 그 포인트는 평가에서 제외한다")
    private String pointType;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_kind")
    @Comment("조건 종류. THRESHOLD=아날로그 임계치 비교(operator+level별 threshold) / TOGGLE=디지털 상태값 일치(trigger_value)")
    private RuleConditionKind conditionKind;

    @Enumerated(EnumType.STRING)
    @Comment("THRESHOLD 비교 연산자(GTE/LTE/GT/LT). TOGGLE 이면 사용하지 않는다")
    private RuleOperator operator;

    @Column(name = "trigger_value")
    @Comment("TOGGLE 트리거값. 포인트 값을 on/off 로 정규화해 이 값과 일치하면 조건 성립. THRESHOLD 면 사용하지 않는다")
    private String triggerValue;

    /**
     * 순간적인 노이즈로 알람이 뜨는 것을 막는다. 이 시간은 인메모리로 계측하므로
     * event-server 재기동 시 재계측된다(best-effort).
     */
    @Column(name = "sustain_sec")
    @Builder.Default
    @Comment("지속 시간(초). 조건이 이 시간만큼 계속 성립해야 발생시킨다. 0=즉시. 인메모리 계측이라 재기동 시 초기화")
    private int sustainSec = 0;

    // cooldown_sec 은 2026-09-30 삭제 — Active 축이 중복을 구조적으로 막아 시간 기반 억제가 필요 없다.
    // 컬럼 drop: scripts/event/2026-09-30-drop-cooldown-sec.sql (코드 배포 전 선실행)

    /**
     * <b>발생</b> 임계는 그대로 두고 <b>해소</b> 임계만 완화한다.
     * 예: 임계 80 / deadband 2 → 80 이상에서 발생하고, 78 아래로 내려가야 해소된다.
     * 이 여유가 없으면 임계 근처에서 값이 흔들릴 때 발생·해소가 초당 반복된다(플래핑).
     */
    @Column(name = "deadband")
    @Comment("해소 판정 여유값. 발생 임계는 그대로, 해소 임계만 완화해 플래핑을 막는다(GTE/GT는 -, LTE/LT는 +). null=0")
    private Double deadband;

    /** 해소된 <b>뒤</b>의 동작만 가른다. 성립 중 억제는 두 모드 모두 동일하다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "suppress_mode", nullable = false, length = 16)
    @Builder.Default
    @Comment("해소 후 재발생 억제 범위. WHILE_ACTIVE=성립 중에만(기본) / UNTIL_ACKED=사람이 확인할 때까지")
    private SuppressMode suppressMode = SuppressMode.WHILE_ACTIVE;

    @Builder.Default
    @Comment("규칙 사용 여부. false 면 event-server 가 평가 인덱스에 아예 싣지 않는다(최대 30초 내 반영)")
    private Boolean active = true;

    /**
     * 심각도 등급 목록. {@code orphanRemoval=true} 라 규칙 수정 시 전체 교체가 가능하고,
     * DB 쪽에도 {@code ON DELETE CASCADE} 가 걸려 있어 규칙을 지우면 레벨도 함께 사라진다.
     */
    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EventRuleLevel> levels = new ArrayList<>();

    public void addLevel(EventRuleLevel level) {
        level.setRule(this);
        this.levels.add(level);
    }

    public void clearLevels() {
        this.levels.clear();
    }
}
