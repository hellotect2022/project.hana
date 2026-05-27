package com.mpole.hana.digitaltwin.iam.role.dto;

import com.mpole.hana.digitaltwin.iam.user.entity.User;
import lombok.Builder;

@Builder
public record RoleUserResponse(
        Long userId,
        String loginId,
        String username
) {
    public static RoleUserResponse from(User user) {
        return RoleUserResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .username(user.getUsername())
                .build();
    }
}
