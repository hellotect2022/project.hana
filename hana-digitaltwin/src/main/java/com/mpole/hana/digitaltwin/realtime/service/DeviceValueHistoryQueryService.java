package com.mpole.hana.digitaltwin.realtime.service;

import com.mpole.hana.digitaltwin.realtime.dto.DeviceValueHistoryResponse;
import com.mpole.hana.digitaltwin.realtime.repository.DeviceValueHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 디바이스 실시간 값 이력 조회(읽기 전용). 쓰기는 event-server(R2DBC append).
 */
@Service
@RequiredArgsConstructor
public class DeviceValueHistoryQueryService {

    private final DeviceValueHistoryRepository repository;

    @Transactional(readOnly = true)
    public Page<DeviceValueHistoryResponse> getHistory(
            Long deviceId, OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        return repository.findByDeviceAndPeriod(deviceId, from, to, pageable)
                .map(DeviceValueHistoryResponse::from);
    }
}
