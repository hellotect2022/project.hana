package com.mpole.hana.digitaltwin.device.repository;

import com.mpole.hana.digitaltwin.device.entity.DevicePlacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DevicePlacementRepository extends JpaRepository<DevicePlacement, Long> {
    Optional<DevicePlacement> findByDevice_DeviceId(Long deviceId);
}
