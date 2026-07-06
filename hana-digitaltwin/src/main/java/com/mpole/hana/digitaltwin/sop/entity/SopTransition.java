package com.mpole.hana.digitaltwin.sop.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tbl_sop_transition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SopTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transition_id")
    private Long transitionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private SopTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_step_id", nullable = false)
    private SopStep fromStep;

    @Column(name = "trigger_key", nullable = false, length = 64)
    private String triggerKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_step_id")
    private SopStep toStep;

    @Column(name = "result_status", length = 32)
    private String resultStatus;
}
