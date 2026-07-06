package com.mpole.hana.digitaltwin.space.service;

import com.mpole.hana.digitaltwin.buildingLighting.domain.CommStatus;
import com.mpole.hana.digitaltwin.buildingLighting.domain.LightingState;
import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingZoneStatusResponse;
import com.mpole.hana.digitaltwin.buildingLighting.service.BuildingLightingStatusService;
import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;
import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;
import com.mpole.hana.digitaltwin.location.repository.UnityLocFloorRepository;
import com.mpole.hana.digitaltwin.location.repository.UnityLocZoneRepository;
import com.mpole.hana.digitaltwin.space.dto.ZoneDisplayPolicyRequest;
import com.mpole.hana.digitaltwin.space.dto.ZoneDisplayPolicyResponse;
import com.mpole.hana.digitaltwin.space.dto.ZonePolicyUpdateItem;
import com.mpole.hana.digitaltwin.space.dto.ZonePolicyWithZoneRequest;
import com.mpole.hana.digitaltwin.space.dto.ZoneStatusSummaryResponse;
import com.mpole.hana.digitaltwin.space.entity.ZoneDisplayPolicy;
import com.mpole.hana.digitaltwin.space.entity.ZoneSystemType;
import com.mpole.hana.digitaltwin.space.repository.ZoneDisplayPolicyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZoneDisplayPolicyService {

    private final ZoneDisplayPolicyRepository policyRepository;
    private final UnityLocZoneRepository zoneRepository;
    private final UnityLocFloorRepository floorRepository;
    private final BuildingLightingStatusService lightingStatusService;

    @Transactional(readOnly = true)
    public List<ZoneDisplayPolicyResponse> getPolicies(ZoneSystemType systemType) {
        return policyRepository.findAllBySystemTypeWithZone(systemType).stream()
                .map(ZoneDisplayPolicyResponse::from)
                .toList();
    }

    /**
     * 구역 현황 통계(전체/ON/OFF/Fault).
     * 조명은 buildingLighting 집계로 zone별 상태를 매핑. 화재감지는 상태 소스가 없어 전체수만 제공.
     */
    @Transactional(readOnly = true)
    public ZoneStatusSummaryResponse getSummary(ZoneSystemType systemType) {
        List<ZoneDisplayPolicy> policies = policyRepository.findAllBySystemTypeWithZone(systemType);
        long total = policies.size();

        if (systemType != ZoneSystemType.LIGHTING) {
            return new ZoneStatusSummaryResponse(total, 0, 0, 0);
        }

        Map<Long, LightingZoneStatusResponse> byZone = lightingStatusService.getZoneStatuses().stream()
                .collect(Collectors.toMap(LightingZoneStatusResponse::zoneId, Function.identity(), (a, b) -> a));

        long on = 0, off = 0, fault = 0;
        for (ZoneDisplayPolicy p : policies) {
            Long zoneId = p.getZone() != null ? p.getZone().getZoneId() : null;
            LightingZoneStatusResponse s = zoneId != null ? byZone.get(zoneId) : null;
            if (s != null && s.commStatus() != null && s.commStatus() != CommStatus.NORMAL) {
                fault++;
            } else if (s != null && s.state() == LightingState.ON) {
                on++;
            } else {
                off++;
            }
        }
        return new ZoneStatusSummaryResponse(total, on, off, fault);
    }

    @Transactional
    public ZoneDisplayPolicyResponse create(ZoneDisplayPolicyRequest request) {
        ZoneSystemType systemType = ZoneSystemType.fromCode(request.systemType());
        if (policyRepository.existsByZone_ZoneIdAndSystemType(request.zoneId(), systemType)) {
            throw new IllegalStateException("이미 해당 구역의 표현정책이 있습니다.");
        }
        UnityLocZone zone = zoneRepository.findById(request.zoneId())
                .orElseThrow(() -> new EntityNotFoundException("Zone 이 존재하지 않습니다. id=" + request.zoneId()));

        ZoneDisplayPolicy policy = ZoneDisplayPolicy.builder()
                .zone(zone)
                .systemType(systemType)
                .displayName(request.displayName())
                .colorCode(request.colorCode())
                .opacity(request.opacity() != null ? request.opacity() : 30)
                .active(request.active() == null || request.active())
                .evacuationAutoShow(request.evacuationAutoShow())
                .build();
        return ZoneDisplayPolicyResponse.from(policyRepository.save(policy));
    }

    /** 조명/화재 구역 추가 — 새 Zone(3D) + 표현정책을 한 트랜잭션에 생성 */
    @Transactional
    public ZoneDisplayPolicyResponse createWithNewZone(ZonePolicyWithZoneRequest req) {
        ZoneSystemType systemType = ZoneSystemType.fromCode(req.systemType());
        UnityLocFloor floor = floorRepository.findById(req.floorId())
                .orElseThrow(() -> new EntityNotFoundException("층을 찾을 수 없습니다. id=" + req.floorId()));

        UnityLocZone zone = zoneRepository.save(
                UnityLocZone.builder()
                        .floor(floor)
                        .zoneName(req.zoneName().trim())
                        .zoneMeshName(req.zoneMeshName().trim())
                        .meshType("3D") // 조명/화재 구역은 항상 3D
                        .build());

        ZoneDisplayPolicy policy = ZoneDisplayPolicy.builder()
                .zone(zone)
                .systemType(systemType)
                .displayName(req.displayName())
                .colorCode(req.colorCode())
                .opacity(req.opacity() != null ? req.opacity() : 30)
                .active(req.active() == null || req.active())
                .evacuationAutoShow(req.evacuationAutoShow())
                .build();
        return ZoneDisplayPolicyResponse.from(policyRepository.save(policy));
    }

    @Transactional
    public void saveAll(List<ZonePolicyUpdateItem> items) {
        if (items == null || items.isEmpty()) return;
        Map<Long, ZoneDisplayPolicy> byId = policyRepository.findAllById(
                        items.stream().map(ZonePolicyUpdateItem::policyId).toList()).stream()
                .collect(Collectors.toMap(ZoneDisplayPolicy::getPolicyId, Function.identity()));

        for (ZonePolicyUpdateItem item : items) {
            ZoneDisplayPolicy p = byId.get(item.policyId());
            if (p == null) continue;
            p.updatePolicy(item.displayName(), item.colorCode(), item.opacity(), item.active(), item.evacuationAutoShow());
        }
    }

    @Transactional
    public void delete(Long policyId) {
        if (!policyRepository.existsById(policyId)) {
            throw new EntityNotFoundException("표현정책이 존재하지 않습니다. id=" + policyId);
        }
        policyRepository.deleteById(policyId);
    }
}
