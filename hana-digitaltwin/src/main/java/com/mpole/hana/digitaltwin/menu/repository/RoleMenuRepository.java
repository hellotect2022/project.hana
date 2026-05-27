package com.mpole.hana.digitaltwin.menu.repository;

import com.mpole.hana.digitaltwin.menu.entity.RoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleMenuRepository extends JpaRepository<RoleMenu,Long> {

    void deleteByRole_RoleId(Long roleId);

    @Query("""
            SELECT rm.menu.menuId
            FROM RoleMenu rm
            WHERE rm.role.roleId = :roleId
            ORDER BY rm.menu.sortOrder ASC
            """)
    List<Long> findMenuIdsByRoleId(@Param("roleId") Long roleId);
}
