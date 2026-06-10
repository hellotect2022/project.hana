package com.mpole.hana.digitaltwin.realtime.repository;

import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceCurrentValueRepository extends JpaRepository<DeviceCurrentValue,Long> {
}
