package com.mpole.hana.digitaltwin.sop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tbl_sop_instance_item_state", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sop_instance_item", columnNames = {"instance_id", "item_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SopInstanceItemState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean completed = false;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by")
    private Long completedBy;
}
