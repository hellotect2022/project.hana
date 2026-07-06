package com.mpole.hana.digitaltwin.sop.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "tbl_sop_button")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SopButton {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "button_id")
    private Long buttonId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private SopStepItem item;

    @Column(name = "button_key", nullable = false, length = 64)
    private String buttonKey;

    @Column(nullable = false)
    private String label;

    @Column(length = 32)
    @Builder.Default
    private String style = "default";

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private SopButtonActionType actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_config", columnDefinition = "jsonb")
    private Map<String, Object> actionConfig;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
