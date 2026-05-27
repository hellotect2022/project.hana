package com.mpole.hana.digitaltwin.iam.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "tbl_refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refresh_token_login_device", columnNames = {"login_id", "device_id"})
        },
        indexes = {
                @Index(name = "idx_refresh_token_login_id", columnList = "login_id"),
                @Index(name = "idx_refresh_token_expiry_date", columnList = "expiry_date")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Setter
    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Setter
    @Column(name = "expiry_date", nullable = false)
    private OffsetDateTime expiryDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "last_activity_at")
    private OffsetDateTime lastActivityAt;

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiryDate);
    }

    public void updateLastUsed() {
        this.lastUsedAt = OffsetDateTime.now();
    }

    public void updateActivity() {
        this.lastActivityAt = OffsetDateTime.now();
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.lastUsedAt = now;
        this.lastActivityAt = now;
    }
}
