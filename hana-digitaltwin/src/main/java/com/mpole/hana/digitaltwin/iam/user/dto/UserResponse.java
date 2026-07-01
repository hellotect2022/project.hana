package com.mpole.hana.digitaltwin.iam.user.dto;

import com.mpole.hana.digitaltwin.iam.user.entity.User;

import java.time.OffsetDateTime;
import java.util.List;

public record UserResponse(
        Long userId,
        String loginId,
        String username,
        String email,
        Boolean active,
        Boolean accountNonLocked,
        Integer failedLoginAttempts,
        OffsetDateTime lastLoginDate,
        OffsetDateTime createdAt,
        List<RoleBrief> roles
) {
    /** 사용자에 매핑된 권한(역할) 요약 */
    public record RoleBrief(Long roleId, String roleName) {}

    public static UserResponse from(User user) {
        return from(user, List.of());
    }

    public static UserResponse from(User user, List<RoleBrief> roles) {
        return new UserResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getUsername(),
                user.getEmail(),
                user.getActive(),
                user.getAccountNonLocked(),
                user.getFailedLoginAttempts(),
                user.getLastLoginDate(),
                user.getCreatedAt(),
                roles == null ? List.of() : roles
        );
    }
}
