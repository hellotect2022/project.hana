package com.mpole.hana.digitaltwin.systemMenu.repository;

import com.mpole.hana.digitaltwin.systemMenu.entity.DeviceSystemCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceSystemCategoryRepository extends JpaRepository<DeviceSystemCategory, Long> {

    void deleteByDeviceSystem_SystemId(Long systemId);
}
