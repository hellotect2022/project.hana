package com.mpole.hana.digitaltwin.sop.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "tbl_sop_action_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SopActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "button_id")
    private Long buttonId;

    @Column(name = "action_type", length = 64)
    private String actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private Map<String, Object> requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private Map<String, Object> responsePayload;

    @Column(length = 16)
    private String result;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
