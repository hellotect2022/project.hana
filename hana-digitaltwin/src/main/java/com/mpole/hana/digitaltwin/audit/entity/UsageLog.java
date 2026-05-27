package com.mpole.hana.digitaltwin.audit.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tbl_log_usage", indexes = {
        @Index(name = "idx_usage_log_created", columnList = "created_at"),
        @Index(name = "idx_usage_log_login_id", columnList = "login_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UsageLog extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usage_log_id")
    private Long usageLogId;

    @Column(name = "login_id", length = 100)
    private String loginId;

    @Column(name = "menu_label", length = 200)
    private String menuLabel;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

}
