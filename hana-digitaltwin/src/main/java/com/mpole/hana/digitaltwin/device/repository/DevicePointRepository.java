package com.mpole.hana.digitaltwin.device.repository;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DevicePointRepository extends JpaRepository<DevicePoint,Long> {

    boolean existsByPointKey(String pointKey);

    boolean existsByPointKeyAndPointIdNot(String pointKey, Long pointId);

    boolean existsByDevice_DeviceIdAndTagName(Long deviceId, String tagName);

    java.util.Optional<DevicePoint> findByDevice_DeviceIdAndTagName(Long deviceId, String tagName);

    void deleteByDevice_DeviceIdAndTagNameIn(Long deviceId, Collection<String> tagNames);

    List<DevicePoint> findAllByDevice_DeviceId(Long deviceId);

    void deleteByDevice_DeviceIdIn(Collection<Long> deviceIds);
}
