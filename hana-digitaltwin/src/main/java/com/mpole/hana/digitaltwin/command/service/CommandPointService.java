package com.mpole.hana.digitaltwin.command.service;

import com.mpole.hana.digitaltwin.command.dto.CommandPointRequest;
import com.mpole.hana.digitaltwin.command.dto.CommandPointResponse;
import com.mpole.hana.digitaltwin.command.entity.CommandPoint;
import com.mpole.hana.digitaltwin.command.entity.CommandPoint.CommandOption;
import com.mpole.hana.digitaltwin.command.entity.CommandType;
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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : 0)
                .active(req.active() != null ? req.active() : Boolean.TRUE)
                .build();
        applyTypeFields(entity, req);

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
        applyTypeFields(entity, req);
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

    /** 미지정은 TOGGLE — commandType 도입 이전 클라이언트와의 하위호환. */
    private CommandType resolveType(CommandPointRequest req) {
        return req.commandType() != null ? req.commandType() : CommandType.TOGGLE;
    }

    /**
     * 유형과 <b>그 유형이 쓰는 필드만</b> 저장하고 나머지는 null 로 지운다.
     *
     * <p>이렇게 하지 않으면 RANGE 제어 포인트에 {@code onValue="1"} 이 남는 식으로
     * 의미 없는 값이 DB 와 응답에 계속 따라다닌다. 유형을 바꿔 저장하면 이전 유형의
     * 설정도 여기서 함께 정리된다(예: RANGE → TOGGLE 시 min/max 제거).</p>
     *
     * <p>클라이언트가 무엇을 보내든 서버가 최종 정리한다 — 신뢰 경계는 여기다.</p>
     */
    private void applyTypeFields(CommandPoint entity, CommandPointRequest req) {
        CommandType type = resolveType(req);
        boolean toggle = type == CommandType.TOGGLE;
        boolean range = type == CommandType.RANGE;
        boolean enumType = type == CommandType.ENUM;

        entity.setCommandType(type);
        entity.setOnValue(toggle ? trimToNull(req.onValue()) : null);
        entity.setOffValue(toggle ? trimToNull(req.offValue()) : null);
        entity.setOnLabel(toggle ? trimToNull(req.onLabel()) : null);
        entity.setOffLabel(toggle ? trimToNull(req.offLabel()) : null);
        entity.setMinValue(range ? req.minValue() : null);
        entity.setMaxValue(range ? req.maxValue() : null);
        entity.setStepValue(range ? req.stepValue() : null);
        entity.setUnit(range ? trimToNull(req.unit()) : null);
        entity.setOptions(enumType ? req.options() : null);
    }

    private static String trimToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
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
        validateByType(req);
    }

    /**
     * 유형별 필수값 검증.
     * <p>DB CHECK 제약 대신 여기서 막는다 — 유형이 늘 때마다 DDL 을 바꾸지 않기 위해서다.
     * 설정이 빠진 채 저장되면 제어 시점에야 실패하므로 등록 단계에서 잡는다.</p>
     */
    private void validateByType(CommandPointRequest req) {
        switch (resolveType(req)) {
            case TOGGLE -> {
                if (isBlank(req.onValue()) || isBlank(req.offValue())) {
                    throw new IllegalArgumentException("TOGGLE 은 ON/OFF 전송값이 모두 필요합니다.");
                }
            }
            case ENUM -> {
                List<CommandOption> options = req.options();
                if (options == null || options.isEmpty()) {
                    throw new IllegalArgumentException("ENUM 은 선택지(options)가 1개 이상 필요합니다.");
                }
                Set<String> seen = new HashSet<>();
                for (CommandOption o : options) {
                    if (o == null || isBlank(o.value()) || isBlank(o.label())) {
                        throw new IllegalArgumentException("ENUM 선택지는 value 와 label 이 모두 필요합니다.");
                    }
                    if (!seen.add(o.value().trim())) {
                        throw new IllegalArgumentException("ENUM 선택지의 value 가 중복됩니다. value=" + o.value());
                    }
                }
            }
            case RANGE -> {
                if (req.minValue() == null || req.maxValue() == null) {
                    throw new IllegalArgumentException("RANGE 는 최솟값·최댓값이 모두 필요합니다.");
                }
                if (req.maxValue() <= req.minValue()) {
                    throw new IllegalArgumentException(
                            "RANGE 의 최댓값은 최솟값보다 커야 합니다. min=" + req.minValue() + ", max=" + req.maxValue());
                }
                if (req.stepValue() != null && req.stepValue() <= 0) {
                    throw new IllegalArgumentException("RANGE 의 단계(step)는 0보다 커야 합니다. step=" + req.stepValue());
                }
            }
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
