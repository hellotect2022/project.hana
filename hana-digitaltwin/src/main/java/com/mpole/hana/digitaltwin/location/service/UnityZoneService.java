package com.mpole.hana.digitaltwin.location.service;

import com.mpole.hana.digitaltwin.location.dto.UnityFloorOptionResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityZoneAdminResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityZoneRequest;
import com.mpole.hana.digitaltwin.location.entity.UnityLocFloor;
import com.mpole.hana.digitaltwin.location.entity.UnityLocZone;
import com.mpole.hana.digitaltwin.location.repository.UnityLocFloorRepository;
import com.mpole.hana.digitaltwin.location.repository.UnityLocZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 콜라이더(존, tbl_unity_loc_zone) 관리 — 등록/조회/수정 */
@Service
@RequiredArgsConstructor
public class UnityZoneService {

    private final UnityLocZoneRepository zoneRepository;
    private final UnityLocFloorRepository floorRepository;

    /** 층 선택용 옵션 목록 */
    @Transactional(readOnly = true)
    public List<UnityFloorOptionResponse> getFloorOptions() {
        return floorRepository.findAllByOrderByFloorNumAsc().stream()
                .map(UnityFloorOptionResponse::from)
                .toList();
    }

    /** 콜라이더 목록 — floorId 가 있으면 층별 필터 */
    @Transactional(readOnly = true)
    public List<UnityZoneAdminResponse> getZones(Long floorId) {
        List<UnityLocZone> zones = (floorId != null)
                ? zoneRepository.findByFloorIdWithFloor(floorId)
                : zoneRepository.findAllWithFloor();
        return zones.stream().map(UnityZoneAdminResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UnityZoneAdminResponse getZone(Long zoneId) {
        return UnityZoneAdminResponse.from(getZoneOrThrow(zoneId));
    }

    /** 콜라이더 등록 — mesh_name 중복 검사 */
    @Transactional
    public UnityZoneAdminResponse createZone(UnityZoneRequest request) {
        String meshName = request.zoneMeshName().trim();
        if (zoneRepository.existsByZoneMeshName(meshName)) {
            throw new IllegalStateException("이미 사용 중인 mesh_name 입니다: " + meshName);
        }
        UnityLocFloor floor = getFloorOrThrow(request.floorId());
        UnityLocZone zone = UnityLocZone.builder()
                .floor(floor)
                .zoneName(request.zoneName().trim())
                .zoneMeshName(meshName)
                .meshType(normalizeMeshType(request.meshType()))
                .build();
        return UnityZoneAdminResponse.from(zoneRepository.save(zone));
    }

    /** 콜라이더 수정 — mesh_name 중복 검사(자기 자신 제외) */
    @Transactional
    public UnityZoneAdminResponse updateZone(Long zoneId, UnityZoneRequest request) {
        UnityLocZone zone = getZoneOrThrow(zoneId);
        String meshName = request.zoneMeshName().trim();
        if (zoneRepository.existsByZoneMeshNameAndZoneIdNot(meshName, zoneId)) {
            throw new IllegalStateException("이미 사용 중인 mesh_name 입니다: " + meshName);
        }
        UnityLocFloor floor = getFloorOrThrow(request.floorId());
        zone.updateInfo(floor, request.zoneName().trim(), meshName, normalizeMeshType(request.meshType()));
        return UnityZoneAdminResponse.from(zone);
    }

    /** meshType 정규화: "3D" 만 3D, 그 외(미지정 포함)는 기본 2D */
    private String normalizeMeshType(String meshType) {
        return "3D".equalsIgnoreCase(meshType != null ? meshType.trim() : null) ? "3D" : "2D";
    }

    private UnityLocZone getZoneOrThrow(Long zoneId) {
        return zoneRepository.findById(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("콜라이더(존)를 찾을 수 없습니다. id=" + zoneId));
    }

    private UnityLocFloor getFloorOrThrow(Long floorId) {
        return floorRepository.findById(floorId)
                .orElseThrow(() -> new IllegalArgumentException("층을 찾을 수 없습니다. id=" + floorId));
    }
}
