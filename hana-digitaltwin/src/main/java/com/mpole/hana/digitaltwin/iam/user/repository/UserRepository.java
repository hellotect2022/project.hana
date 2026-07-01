package com.mpole.hana.digitaltwin.iam.user.repository;

import com.mpole.hana.digitaltwin.iam.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    @Query(value = """
            SELECT DISTINCT u FROM User u
            LEFT JOIN UserRole ur ON ur.user = u
            WHERE (:active IS NULL OR u.active = :active)
              AND (:loginId IS NULL OR u.loginId LIKE %:loginId%)
              AND (:username IS NULL OR u.username LIKE %:username%)
              AND (:roleId IS NULL OR ur.role.roleId = :roleId)
            ORDER BY u.userId
            """,
            countQuery = """
            SELECT COUNT(DISTINCT u) FROM User u
            LEFT JOIN UserRole ur ON ur.user = u
            WHERE (:active IS NULL OR u.active = :active)
              AND (:loginId IS NULL OR u.loginId LIKE %:loginId%)
              AND (:username IS NULL OR u.username LIKE %:username%)
              AND (:roleId IS NULL OR ur.role.roleId = :roleId)
            """)
    Page<User> findUsersByCondition(
            @Param("active") Boolean active,
            @Param("loginId") String loginId,
            @Param("username") String username,
            @Param("roleId") Long roleId,
            Pageable pageable
    );

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
