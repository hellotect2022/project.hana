package com.mpole.hana.digitaltwin.system.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "tbl_log_retention_policy",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_log_retention_policy_key", columnNames = "policy_key")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LogRetentionPolicy extends DateEntity {

    public static final String KEY_ACCESS_LOG = "ACCESS_LOG";
    public static final String KEY_USAGE_LOG = "USAGE_LOG";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_key", nullable = false, length = 64)
    @Comment("ACCESS_LOG, USAGE_LOG 등")
    private String policyKey;

    @Column(name = "retention_days", nullable = false)
    @Comment("보관 일수 (이전 데이터 삭제)")
    private int retentionDays;

    @Column(length = 255)
    private String description;

    public static LogRetentionPolicy create(String policyKey, int retentionDays, String description) {
        return LogRetentionPolicy.builder()
                .policyKey(policyKey)
                .retentionDays(retentionDays)
                .description(description)
                .build();
    }

    public void updateRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
        super.setUpdatedAt(OffsetDateTime.now());
    }

}
