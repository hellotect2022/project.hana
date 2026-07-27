package com.mpole.hana.digitaltwin.sop.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import com.mpole.hana.digitaltwin.sop.enums.SopInstanceStatus;
import com.mpole.hana.digitaltwin.sop.model.SopTemplateBody;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * SOP 실행 인스턴스 (v2 런타임).
 * 생성 시점 템플릿 body 를 structureSnapshot 으로 고정(불변), 진행 결과는 result(JSONB, 방식 B)에 한 번에 저장.
 * event/template 은 느슨 결합(ID 참조).
 */
@Entity
@Table(name = "tbl_sop_instance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sop_instance_event", columnNames = "event_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SopInstance extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instance_id")
    private Long instanceId;

    @Column(name = "event_id")
    @Comment("이벤트 1:1 (event 재연결 전이라 nullable — 단독 생성 허용)")
    private Long eventId;

    @Column(name = "template_id", nullable = false)
    @Comment("원본 템플릿(느슨 참조)")
    private Long templateId;

    @Column(name = "template_code")
    private String templateCode;

    @Column(name = "locked_revision", nullable = false)
    @Comment("생성 시점 template.revision 고정")
    private Integer lockedRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structure_snapshot", columnDefinition = "jsonb", nullable = false)
    @Comment("생성 시점 body 복사(불변)")
    private SopTemplateBody structureSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    @Comment("실행 결과(방식 B) — stepStates/checked/chosen/log 등 자유 JSONB")
    private Map<String, Object> result;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private SopInstanceStatus status = SopInstanceStatus.PENDING;

    @Column(name = "current_step_no")
    private Integer currentStepNo;

    @Column(name = "result_status", length = 32)
    @Comment("종료 결과(예: FALSE_ALARM)")
    private String resultStatus;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "handled_by_user_id")
    private Long handledByUserId;
}
