package com.mpole.hana.digitaltwin.iam.role.dto;

import com.mpole.hana.digitaltwin.iam.role.entity.Role;
import lombok.Builder;

@Builder
public record RoleResponse(
        Long roleId,
        String roleName,
        String description,
        long userCount
) {
    public static RoleResponse of(Role role, long userCount) {
        return RoleResponse.builder()
                .roleId(role.getRoleId())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .userCount(userCount)
                .build();
    }
}
