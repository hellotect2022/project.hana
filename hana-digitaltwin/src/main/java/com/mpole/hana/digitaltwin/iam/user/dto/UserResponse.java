package com.mpole.hana.digitaltwin.iam.user.dto;

import com.mpole.hana.digitaltwin.iam.user.entity.User;

import java.time.OffsetDateTime;

public record UserResponse(
        Long userId,
        String loginId,
        String username,
        String email,
        Boolean active,
        Boolean accountNonLocked,
        Integer failedLoginAttempts,
        OffsetDateTime lastLoginDate,
        OffsetDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getLoginId(),
                user.getUsername(),
                user.getEmail(),
                user.getActive(),
                user.getAccountNonLocked(),
                user.getFailedLoginAttempts(),
                user.getLastLoginDate(),
                user.getCreatedAt()
        );
    }
}
