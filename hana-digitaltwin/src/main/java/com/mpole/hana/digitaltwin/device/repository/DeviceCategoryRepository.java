package com.mpole.hana.digitaltwin.device.repository;

import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceCategoryRepository extends JpaRepository<DeviceCategory,Long> {
    @Query("""
            SELECT dc 
            FROM DeviceCategory dc 
            LEFT JOIN FETCH dc.parent 
            ORDER BY dc.depth DESC, dc.displayOrder""")
    List<DeviceCategory> findAllWithParent();

    boolean existsByParent(DeviceCategory parent);

    boolean existsByCategoryCodeAndDepth(String categoryCode, Integer depth);
}
