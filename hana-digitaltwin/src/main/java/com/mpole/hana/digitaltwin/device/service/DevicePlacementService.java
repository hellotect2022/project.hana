package com.mpole.hana.digitaltwin.device.service;

import com.mpole.hana.digitaltwin.device.dto.DevicePlacementRequest;
import com.mpole.hana.digitaltwin.device.dto.DevicePlacementResponse;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePlacement;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.location.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DevicePlacementService {
    private final DeviceRepository deviceRepository;
    private final LocationService locationService;

    @Transactional
    public DevicePlacementResponse save(DevicePlacementRequest req) {
        Device device = deviceRepository.fetchDeviceById(req.deviceId());

        device.setDeviceName(req.deviceName());
        device.setDescription(req.description());

        DevicePlacement p = device.getPlacement();

        if (p == null) {
            p = DevicePlacement.builder().device(device).build();
            device.setPlacement(p);
        }

        if (req.unityZoneId() != null) {
            p.setUnityZone(locationService.getZoneReference(req.unityZoneId()));
        } else {
            p.setUnityZone(null);
        }

        p.applyTransform(req.posX(), req.posY(), req.posZ(),
                req.rotX(), req.rotY(), req.rotZ(),
                req.scaleX(), req.scaleY(), req.scaleZ());

        return DevicePlacementResponse.from(device);
    }

    @Transactional
    public List<DevicePlacementResponse> saveAll(List<DevicePlacementRequest> requests) {
        return requests.stream().map(this::save).toList();
    }

    @Transactional
    public void deleteByDeviceIds(List<Long> deviceIds) {
        List<Device> devices = deviceRepository.findAllById(deviceIds);
        devices.forEach(Device::clearPlacement);
    }
}
