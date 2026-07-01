package com.mpole.hana.digitaltwin.iam.role.repository;

import com.mpole.hana.digitaltwin.iam.role.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole,Long> {

    boolean existsByUser_UserIdAndRole_RoleId(Long userId, Long roleId);

    void deleteByUser_UserIdAndRole_RoleId(Long userId, Long roleId);

    /** 해당 권한에 매핑된 사용자 수 (삭제 가능 여부 판단용) */
    long countByRole_RoleId(Long roleId);

    /** 여러 사용자의 역할을 한 번에 조회 (N+1 방지). role 즉시 로딩. */
    @Query("""
            SELECT ur FROM UserRole ur
            JOIN FETCH ur.role
            WHERE ur.user.userId IN :userIds
            """)
    List<UserRole> findWithRoleByUserIds(@Param("userIds") List<Long> userIds);
}
