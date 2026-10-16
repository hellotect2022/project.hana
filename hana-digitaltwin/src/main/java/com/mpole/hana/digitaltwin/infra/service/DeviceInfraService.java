package com.mpole.hana.digitaltwin.infra.service;

import com.mpole.hana.common.constant.InfraType;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.AutoAssignRequest;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.BulkMappingRequest;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.CandidateDeviceResponse;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.ConsistencyResponse;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.MappedDeviceResponse;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.DeviceInfraRequest;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.DeviceInfraResponse;
import com.mpole.hana.digitaltwin.infra.dto.DeviceInfraDtos.InfraMappingRequest;
import com.mpole.hana.digitaltwin.infra.entity.DeviceInfra;
import com.mpole.hana.digitaltwin.infra.repository.DeviceInfraRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 인프라 마스터 관리(설계 v2.2).
 * <p>등록은 관리자 직접(ref_infra_code 입력), 장비 매핑은 자동 산정(NULL 만 채움·수동 우선) + 수동 지정 하이브리드.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceInfraService {

    private final DeviceInfraRepository deviceInfraRepository;
    private final DeviceRepository deviceRepository;

    @Transactional(readOnly = true)
    public List<DeviceInfraResponse> getAll(InfraType type) {
        List<DeviceInfra> list = type != null
                ? deviceInfraRepository.findAllByInfraTypeOrderByInfraNameAsc(type)
                : deviceInfraRepository.findAllByOrderByInfraTypeAscInfraNameAsc();

        Map<Long, Long> countByInfra = deviceInfraRepository.countDevicesByInfra().stream()
                .collect(Collectors.toMap(
                        DeviceInfraRepository.InfraDeviceCountRow::getInfraId,
                        DeviceInfraRepository.InfraDeviceCountRow::getCnt));

        return list.stream()
                .map(e -> DeviceInfraResponse.from(e, countByInfra.getOrDefault(e.getInfraId(), 0L)))
                .toList();
    }

    @Transactional
    public DeviceInfraResponse create(DeviceInfraRequest request) {
        validateRequest(request);
        String refCode = trimToNull(request.refInfraCode());
        if (refCode != null && deviceInfraRepository.existsByRefInfraCode(refCode)) {
            throw new IllegalArgumentException("이미 등록된 ref_infra_code 입니다: " + refCode);
        }
        DeviceInfra saved = deviceInfraRepository.save(DeviceInfra.builder()
                .refInfraCode(refCode)
                .infraName(request.infraName().trim())
                .infraDisplayName(trimToNull(request.infraDisplayName()))
                .infraType(request.infraType())
                .active(request.active() == null || request.active())
                .properties(request.properties())
                .build());
        return DeviceInfraResponse.from(saved, 0L);
    }

    @Transactional
    public DeviceInfraResponse update(Long infraId, DeviceInfraRequest request) {
        DeviceInfra infra = deviceInfraRepository.findById(infraId)
                .orElseThrow(() -> new EntityNotFoundException("인프라를 찾을 수 없습니다. id=" + infraId));

        if (request.refInfraCode() != null) {
            String refCode = trimToNull(request.refInfraCode());
            if (refCode != null
                    && !refCode.equals(infra.getRefInfraCode())
                    && deviceInfraRepository.existsByRefInfraCode(refCode)) {
                throw new IllegalArgumentException("이미 등록된 ref_infra_code 입니다: " + refCode);
            }
            infra.setRefInfraCode(refCode);
        }
        if (request.infraName() != null && !request.infraName().isBlank()) {
            infra.setInfraName(request.infraName().trim());
        }
        if (request.infraDisplayName() != null) {   // null = 변경 없음, 빈 문자열 = 지움
            infra.setInfraDisplayName(trimToNull(request.infraDisplayName()));
        }
        if (request.infraType() != null) {
            infra.setInfraType(request.infraType());
        }
        if (request.active() != null) {
            infra.setActive(request.active());
        }
        if (request.properties() != null) {
            infra.setProperties(request.properties());
        }
        long cnt = deviceInfraRepository.countDevicesByInfra().stream()
                .filter(r -> r.getInfraId().equals(infraId))
                .map(DeviceInfraRepository.InfraDeviceCountRow::getCnt)
                .findFirst().orElse(0L);
        return DeviceInfraResponse.from(infra, cnt);
    }

    /**
     * 삭제 — 소속 장비의 infra_id 를 <b>명시적으로 NULL 처리한 뒤</b> 인프라 행만 삭제한다.
     * <p>장비(tbl_device) 행은 절대 삭제하지 않는다. FK 가 ON DELETE SET NULL 이라 DB 도 같은 결과를 보장하지만,
     * 마이그레이션 SQL 미적용 등으로 FK 가 없는 DB 에서도 안전하도록 애플리케이션에서 먼저 해제한다(멱등).</p>
     * <p>해제된 장비는 미매핑 상태가 되어 정합성 리포트에 노출되며, 통신상태(current_value)는 마지막 값을 유지한다.</p>
     */
    @Transactional
    public void delete(Long infraId) {
        if (!deviceInfraRepository.existsById(infraId)) {
            throw new EntityNotFoundException("인프라를 찾을 수 없습니다. id=" + infraId);
        }
        int cleared = deviceInfraRepository.clearInfraFromAllDevices(infraId);
        deviceInfraRepository.deleteById(infraId);
        log.info("[DeviceInfra] 인프라 삭제 — infraId={}, 매핑 해제 장비 {}건(장비 행은 유지)", infraId, cleared);
    }

    /** 장비 소속 수동 지정/해제(infraId=null → 해제). 수동 지정은 자동 산정이 덮지 않는다(NULL-only 규칙). */
    @Transactional
    public void setMapping(InfraMappingRequest request) {
        if (request.deviceId() == null) {
            throw new IllegalArgumentException("deviceId 는 필수입니다.");
        }
        Device device = deviceRepository.findById(request.deviceId())
                .orElseThrow(() -> new EntityNotFoundException("장비를 찾을 수 없습니다. id=" + request.deviceId()));
        if (request.infraId() != null && !deviceInfraRepository.existsById(request.infraId())) {
            throw new EntityNotFoundException("인프라를 찾을 수 없습니다. id=" + request.infraId());
        }
        device.setInfraId(request.infraId());
    }

    /** 인프라 기준 매핑 장비 목록. */
    @Transactional(readOnly = true)
    public List<MappedDeviceResponse> getMappedDevices(Long infraId) {
        requireInfra(infraId);
        return deviceInfraRepository.findMappedDevices(infraId).stream()
                .map(r -> new MappedDeviceResponse(
                        r.getDeviceId(), r.getDeviceName(), r.getDeviceDisplayName(), r.getCategoryName()))
                .toList();
    }

    /** 매핑 후보(이 인프라 소속이 아닌 장비, 키워드 검색 · 최대 100건). */
    @Transactional(readOnly = true)
    public List<CandidateDeviceResponse> getMappingCandidates(Long infraId, String keyword) {
        requireInfra(infraId);
        String kw = keyword == null ? "" : keyword.trim();
        return deviceInfraRepository.findMappingCandidates(infraId, kw).stream()
                .map(r -> new CandidateDeviceResponse(
                        r.getDeviceId(), r.getDeviceName(), r.getDeviceDisplayName(),
                        r.getCategoryName(), r.getInfraId(), r.getInfraName()))
                .toList();
    }

    /** 인프라 기준 일괄 매핑 — 타 인프라 소속 장비도 이 인프라로 이동(명시적 수동 지정). */
    @Transactional
    public int bulkAssign(Long infraId, BulkMappingRequest request) {
        requireInfra(infraId);
        if (request == null || request.deviceIds() == null || request.deviceIds().isEmpty()) {
            throw new IllegalArgumentException("deviceIds 는 1개 이상이어야 합니다.");
        }
        return deviceInfraRepository.bulkAssignDevices(infraId, request.deviceIds());
    }

    /** 인프라 기준 일괄 해제(이 인프라 소속 장비만). */
    @Transactional
    public int bulkUnassign(Long infraId, BulkMappingRequest request) {
        requireInfra(infraId);
        if (request == null || request.deviceIds() == null || request.deviceIds().isEmpty()) {
            throw new IllegalArgumentException("deviceIds 는 1개 이상이어야 합니다.");
        }
        return deviceInfraRepository.bulkUnassignDevices(infraId, request.deviceIds());
    }

    private void requireInfra(Long infraId) {
        if (!deviceInfraRepository.existsById(infraId)) {
            throw new EntityNotFoundException("인프라를 찾을 수 없습니다. id=" + infraId);
        }
    }

    /** 자동 산정(전체) — 관리자 명시 액션. infra_id IS NULL 인 장비만 채운다(수동 우선). */
    @Transactional
    public int autoAssign(AutoAssignRequest request) {
        String excludeCsv = request != null && request.excludeRefCodes() != null
                ? String.join(",", request.excludeRefCodes())
                : "";
        int assigned = deviceInfraRepository.autoAssignAll(excludeCsv);
        log.info("[DeviceInfra] 자동 산정 완료 — {}개 장비 매핑(제외코드: {})", assigned, excludeCsv.isEmpty() ? "없음" : excludeCsv);
        return assigned;
    }

    @Transactional(readOnly = true)
    public ConsistencyResponse getConsistency() {
        List<ConsistencyResponse.DeviceBrief> unmapped = deviceInfraRepository.findUnmappedXnDevices().stream()
                .map(r -> new ConsistencyResponse.DeviceBrief(r.getDeviceId(), r.getDeviceName()))
                .toList();
        List<ConsistencyResponse.MultiRefDevice> multiRef = deviceInfraRepository.findMultiRefDevices().stream()
                .map(r -> new ConsistencyResponse.MultiRefDevice(r.getDeviceId(), r.getDeviceName(), r.getRefCount()))
                .toList();
        return new ConsistencyResponse(unmapped, multiRef);
    }

    private static void validateRequest(DeviceInfraRequest request) {
        if (request == null || request.infraName() == null || request.infraName().isBlank()) {
            throw new IllegalArgumentException("infraName 은 필수입니다.");
        }
        if (request.infraType() == null) {
            throw new IllegalArgumentException("infraType 은 필수입니다.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
