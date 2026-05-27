package com.mpole.hana.digitaltwin.systemMenu.repository;

import com.mpole.hana.digitaltwin.systemMenu.entity.RoleDeviceSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleDeviceSystemRepository extends JpaRepository<RoleDeviceSystem, Long> {

    void deleteByRole_RoleId(Long roleId);

    void deleteByDeviceSystem_SystemId(Long systemId);

    @Query("""
            SELECT rds.deviceSystem.systemId
            FROM RoleDeviceSystem rds
            WHERE rds.role.roleId = :roleId
            ORDER BY rds.deviceSystem.sortOrder ASC
            """)
    List<Long> findSystemIdsByRoleId(@Param("roleId") Long roleId);
}
