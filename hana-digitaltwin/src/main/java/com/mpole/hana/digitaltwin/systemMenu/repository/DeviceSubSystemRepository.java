package com.mpole.hana.digitaltwin.systemMenu.repository;

import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSubSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceSubSystemRepository extends JpaRepository<DeviceSubSystem, Long> {

    List<DeviceSubSystem> findByDeviceSystem_SystemIdOrderBySortOrderAsc(Long systemId);

    @Query("""
            SELECT ss
            FROM DeviceSubSystem ss
            LEFT JOIN FETCH ss.categoryMappings cm
            LEFT JOIN FETCH cm.category
            WHERE ss.subSystemId = :subSystemId
            """)
    Optional<DeviceSubSystem> findByIdWithCategories(@Param("subSystemId") Long subSystemId);
}
