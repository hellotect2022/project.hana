package com.mpole.hana.digitaltwin.systemMenu.repository;

import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface DeviceSystemRepository extends JpaRepository<DeviceSystem, Long> {

    boolean existsBySystemName(String systemName);

    boolean existsBySystemCode(String systemCode);

    List<DeviceSystem> findAllByOrderBySortOrderAsc();

    @Query("""
            SELECT DISTINCT ds
            FROM DeviceSystem ds
            LEFT JOIN FETCH ds.categoryMappings cm
            LEFT JOIN FETCH cm.category
            ORDER BY ds.sortOrder ASC
            """)
    List<DeviceSystem> findAllWithCategories();

    @Query("""
            SELECT ds
            FROM DeviceSystem ds
            LEFT JOIN FETCH ds.categoryMappings
            WHERE ds.systemId = :systemId
            """)
    Optional<DeviceSystem> findByIdWithCategories(@Param("systemId") Long systemId);

    // === 3단 구조(시스템 → subSystem → 카테고리) fetch 쿼리 ===

    @Query("""
            SELECT DISTINCT ds
            FROM DeviceSystem ds
            LEFT JOIN FETCH ds.subSystems ss
            LEFT JOIN FETCH ss.categoryMappings cm
            LEFT JOIN FETCH cm.category
            ORDER BY ds.sortOrder ASC
            """)
    List<DeviceSystem> findAllWithSubSystemsAndCategories();

    @Query("""
            SELECT DISTINCT ds
            FROM DeviceSystem ds
            LEFT JOIN FETCH ds.subSystems ss
            LEFT JOIN FETCH ss.categoryMappings cm
            LEFT JOIN FETCH cm.category
            WHERE ds.systemId = :systemId
            """)
    Optional<DeviceSystem> findByIdWithSubSystemsAndCategories(@Param("systemId") Long systemId);
}
