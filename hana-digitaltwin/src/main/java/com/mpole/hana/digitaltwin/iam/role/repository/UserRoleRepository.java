package com.mpole.hana.digitaltwin.iam.role.repository;

import com.mpole.hana.digitaltwin.iam.role.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole,Long> {

    boolean existsByUser_UserIdAndRole_RoleId(Long userId, Long roleId);

    void deleteByUser_UserIdAndRole_RoleId(Long userId, Long roleId);
}
