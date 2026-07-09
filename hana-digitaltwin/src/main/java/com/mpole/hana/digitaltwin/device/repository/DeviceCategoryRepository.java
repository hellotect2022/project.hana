package com.mpole.hana.digitaltwin.device.repository;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceCategoryRepository extends JpaRepository<DeviceCategory,Long> {
    @Query("""
            SELECT dc 
            FROM DeviceCategory dc 
            LEFT JOIN FETCH dc.parent 
            ORDER BY dc.depth DESC, dc.displayOrder""")
    List<DeviceCategory> findAllWithParent();

    @Query("""
            SELECT dc 
            FROM DeviceCategory dc 
            LEFT JOIN FETCH dc.parent p
            LEFT JOIN FETCH p.parent gp
            WHERE dc.categoryId = :categoryId
            """)
    Optional<DeviceCategory> findWithParentJustThreeDepthById(@Param("categoryId")Long categoryId);

    boolean existsByParent(DeviceCategory parent);

    boolean existsByCategoryCodeAndDepth(String categoryCode, Integer depth);

    long countByDefaultAsset_AssetId(Long assetId);
}
