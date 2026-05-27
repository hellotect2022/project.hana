package com.mpole.hana.digitaltwin.audit.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tbl_log_access", indexes = {
        @Index(name = "idx_access_log_created", columnList = "created_at"),
        @Index(name = "idx_access_log_login_id", columnList = "login_id"),
        @Index(name = "idx_access_log_type", columnList = "log_type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AccessLog extends DateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "access_log_id")
    private Long accessLogId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "log_type", length = 32)
    private AccessLogType logType;

    @Column(name = "login_id", length = 100)
    private String loginId;

    @Column(length = 200)
    private String username;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(columnDefinition = "TEXT")
    private String detail;


}
