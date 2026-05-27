package com.mpole.hana.digitaltwin.iam.user.entity;

import com.mpole.hana.digitaltwin.global.repository.DateEntity;
import com.mpole.hana.digitaltwin.iam.role.entity.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tbl_user", indexes = {
        @Index(name = "idx_user_active", columnList = "active"),
        @Index(name = "idx_user_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User extends DateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "login_id", unique = true, nullable = false, length = 50)
    private String loginId;

    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String email;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "account_non_locked", nullable = false)
    @Builder.Default
    private Boolean accountNonLocked = true;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_password_change_date")
    private OffsetDateTime lastPasswordChangeDate;

    @Column(name = "last_login_date")
    private OffsetDateTime lastLoginDate;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserRole> userRoles = new ArrayList<>();


    public static User create(String loginId, String username, String email, String encodedPassword, Boolean active) {
        return User.builder()
                .loginId(loginId)
                .username(username)
                .email(email)
                .passwordHash(encodedPassword)
                .active(active != null ? active : true)
                .accountNonLocked(true)
                .failedLoginAttempts(0)
                .lastPasswordChangeDate(OffsetDateTime.now())
                .build();
    }

    public void update(String username, String email, Boolean active, Boolean accountNonLocked,
                       Integer failedLoginAttempts, String encodedPassword) {
        this.username = username;
        this.email = email;
        this.active = active;
        this.accountNonLocked = accountNonLocked;
        this.failedLoginAttempts = failedLoginAttempts;
        if (encodedPassword != null && !encodedPassword.isBlank()) {
            this.passwordHash = encodedPassword;
            this.lastPasswordChangeDate = OffsetDateTime.now();
        }
    }

    public void changePassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
        this.lastPasswordChangeDate = OffsetDateTime.now();
    }

    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.lastLoginDate = OffsetDateTime.now();
    }

    public void lockAccount() {
        this.accountNonLocked = false;
    }

    public void unlockAccount() {
        this.accountNonLocked = true;
        this.failedLoginAttempts = 0;
    }

    public boolean isPasswordChangeRequired(int passwordExpiryDays) {
        if (lastPasswordChangeDate == null) {
            return true;
        }
        return OffsetDateTime.now().isAfter(lastPasswordChangeDate.plusDays(passwordExpiryDays));
    }

    public List<String> roleNames() {
        return userRoles.stream()
                .map(userRole -> userRole.getRole().getRoleName())
                .toList();
    }

    @PrePersist
    void prePersist() {
        if (lastPasswordChangeDate == null) {
            lastPasswordChangeDate = super.getUpdatedAt();
        }
    }


}
