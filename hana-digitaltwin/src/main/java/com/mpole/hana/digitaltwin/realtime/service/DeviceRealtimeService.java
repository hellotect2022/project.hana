package com.mpole.hana.digitaltwin.realtime.service;

import com.mpole.hana.digitaltwin.realtime.dto.DeviceRealtimeResponse;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceSnapshotLast;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceSnapshotLastRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceRealtimeService {
    private final DeviceSnapshotLastRepository deviceSnapshotLastRepository;

    @Transactional(readOnly = true)
    public List<DeviceRealtimeResponse> getDeviceStates(List<Long> deviceIds) {
        return deviceSnapshotLastRepository.findAllById(deviceIds)
                .stream()
                .sorted(Comparator.comparing(DeviceSnapshotLast::getDeviceId))
                .map(DeviceRealtimeResponse::from)
                .toList();
    }

//    private DeviceRealtimeResponse toResponse(DeviceSnapshotLast snapshot) {
//        return new DeviceRealtimeResponse(
//                snapshot.getDeviceId(),
//                snapshot.getUpdatedAt(),
//                snapshot.getPoints()
//        );
//    }
}
