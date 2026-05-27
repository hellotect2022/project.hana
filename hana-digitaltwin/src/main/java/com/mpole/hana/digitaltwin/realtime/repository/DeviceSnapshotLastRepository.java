package com.mpole.hana.digitaltwin.realtime.repository;

import com.mpole.hana.digitaltwin.realtime.entity.DeviceSnapshotLast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceSnapshotLastRepository extends JpaRepository<DeviceSnapshotLast,Long> {
}
