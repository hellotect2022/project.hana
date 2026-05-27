package com.mpole.hana.digitaltwin.iam.role.repository;

import com.mpole.hana.digitaltwin.iam.role.dto.RoleResponse;
import com.mpole.hana.digitaltwin.iam.role.dto.RoleUserResponse;
import com.mpole.hana.digitaltwin.iam.role.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role,Long> {

    Optional<Role> findByRoleName(String roleName);
    boolean existsByRoleName(String roleName);

    @Query("""
            SELECT new com.mpole.hana.digitaltwin.iam.role.dto.RoleResponse(
                r.roleId,
                r.roleName,
                r.description,
                COUNT(ur.userRoleId)
            )
            FROM Role r
            LEFT JOIN UserRole ur ON ur.role = r
            GROUP BY r.roleId, r.roleName, r.description
            ORDER BY r.roleId ASC
            """)
    List<RoleResponse> findRoleResponses();

    @Query("""
            SELECT new com.mpole.hana.digitaltwin.iam.role.dto.RoleUserResponse(
                u.userId,
                u.loginId,
                u.username
            )
            FROM User u
            JOIN UserRole ur ON ur.user = u
            WHERE ur.role.roleId = :roleId
            ORDER BY u.userId ASC
            """)
    List<RoleUserResponse> findUsersInRole(@Param("roleId") Long roleId);


    @Query("""
            SELECT new com.mpole.hana.digitaltwin.iam.role.dto.RoleUserResponse(
                u.userId,
                u.loginId,
                u.username
            )
            FROM User u
            WHERE u.userId NOT IN (
                SELECT ur.user.userId
                FROM UserRole ur
                WHERE ur.role.roleId = :roleId
            )
            AND (
                :search IS NULL OR :search = '' OR
                u.loginId LIKE CONCAT('%', :search, '%') OR
                u.username LIKE CONCAT('%', :search, '%')
            )
            ORDER BY u.userId ASC
            """)
    List<RoleUserResponse> findUsersExceptRole(
            @Param("roleId") Long roleId,
            @Param("search") String search
    );
}
