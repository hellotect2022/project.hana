package com.mpole.hana.digitaltwin.sop.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

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

    @Column(name = "event_id", nullable = false, unique = true)
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private SopTemplate template;

    @Column(name = "locked_revision", nullable = false)
    private Integer lockedRevision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structure_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> structureSnapshot;

    @Column(name = "current_step_no")
    private Integer currentStepNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private SopInstanceStatus status = SopInstanceStatus.PENDING;

    @Column(name = "result_status", length = 32)
    private String resultStatus;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "timeout_at")
    private OffsetDateTime timeoutAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "handled_by_user_id")
    private Long handledByUserId;
}
