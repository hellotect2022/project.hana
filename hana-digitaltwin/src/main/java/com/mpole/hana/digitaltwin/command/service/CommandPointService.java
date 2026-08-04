package com.mpole.hana.digitaltwin.command.service;

import com.mpole.hana.digitaltwin.command.dto.CommandPointRequest;
import com.mpole.hana.digitaltwin.command.dto.CommandPointResponse;
import com.mpole.hana.digitaltwin.command.entity.CommandPoint;
import com.mpole.hana.digitaltwin.command.repository.CommandPointRepository;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.device.repository.DevicePointRepository;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 디바이스 제어 포인트(CommandPoint) 관리 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommandPointService {

    private final CommandPointRepository commandPointRepository;
    private final DeviceRepository deviceRepository;
    private final DevicePointRepository devicePointRepository;

    public CommandPointResponse create(CommandPointRequest req) {
        validate(req);
        Device device = findDevice(req.deviceId());
        DevicePoint point = findPointInDevice(req.pointId(), req.deviceId());

        CommandPoint entity = CommandPoint.builder()
                .device(device)
                .point(point)
                .label(req.label())
                .onValue(req.onValue() != null ? req.onValue() : "1")
                .offValue(req.offValue() != null ? req.offValue() : "0")
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .active(req.active() != null ? req.active() : Boolean.TRUE)
                .build();

        CommandPoint saved = commandPointRepository.save(entity);
        log.info("제어 포인트 생성: commandPointId={}, deviceId={}, pointId={}",
                saved.getCommandPointId(), req.deviceId(), req.pointId());
        return CommandPointResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CommandPointResponse> listByDevice(Long deviceId) {
        return commandPointRepository.findByDevice_DeviceIdOrderBySortOrderAsc(deviceId).stream()
                .map(CommandPointResponse::from)
                .toList();
    }

    /** 제어 포인트 전체 페이지 조회(장비 선택 없이). keyword/categoryId 선택 필터. 정렬은 Pageable. */
    @Transactional(readOnly = true)
    public Page<CommandPointResponse> page(String keyword, Long categoryId, Pageable pageable) {
        return commandPointRepository.search(keyword, categoryId, pageable)
                .map(CommandPointResponse::from);
    }

    @Transactional(readOnly = true)
    public CommandPointResponse get(Long id) {
        return CommandPointResponse.from(findOrThrow(id));
    }

    public CommandPointResponse update(Long id, CommandPointRequest req) {
        validate(req);
        CommandPoint entity = findOrThrow(id);
        Device device = findDevice(req.deviceId());
        DevicePoint point = findPointInDevice(req.pointId(), req.deviceId());

        entity.setDevice(device);
        entity.setPoint(point);
        entity.setLabel(req.label());
        entity.setOnValue(req.onValue() != null ? req.onValue() : "1");
        entity.setOffValue(req.offValue() != null ? req.offValue() : "0");
        entity.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        entity.setActive(req.active() != null ? req.active() : Boolean.TRUE);

        CommandPoint saved = commandPointRepository.save(entity);
        log.info("제어 포인트 수정: commandPointId={}", saved.getCommandPointId());
        return CommandPointResponse.from(saved);
    }

    public void delete(Long id) {
        CommandPoint entity = findOrThrow(id);
        commandPointRepository.delete(entity);
        log.info("제어 포인트 삭제: commandPointId={}", id);
    }

    private CommandPoint findOrThrow(Long id) {
        return commandPointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("제어 포인트를 찾을 수 없습니다. id=" + id));
    }

    private Device findDevice(Long deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("장비를 찾을 수 없습니다. id=" + deviceId));
    }

    /** 포인트가 지정 디바이스 소속인지 검증한다. */
    private DevicePoint findPointInDevice(Long pointId, Long deviceId) {
        DevicePoint point = devicePointRepository.findById(pointId)
                .orElseThrow(() -> new IllegalArgumentException("포인트를 찾을 수 없습니다. id=" + pointId));
        Long ownerId = point.getDevice() != null ? point.getDevice().getDeviceId() : null;
        if (!Objects.equals(ownerId, deviceId)) {
            throw new IllegalArgumentException(
                    "포인트가 해당 장비 소속이 아닙니다. pointId=" + pointId + ", deviceId=" + deviceId);
        }
        return point;
    }

    private void validate(CommandPointRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("요청 본문이 필요합니다.");
        }
        if (req.deviceId() == null) {
            throw new IllegalArgumentException("deviceId 는 필수입니다.");
        }
        if (req.pointId() == null) {
            throw new IllegalArgumentException("pointId 는 필수입니다.");
        }
    }
}
