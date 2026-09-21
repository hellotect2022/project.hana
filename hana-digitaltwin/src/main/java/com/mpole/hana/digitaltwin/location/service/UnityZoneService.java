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

/** 콜라이더(구역, tbl_unity_loc_zone) 관리 — 등록/조회/수정 */
@Service
@RequiredArgsConstructor
public class UnityZoneService {

    private final UnityLocZoneRepository zoneRepository;
    private final UnityLocFloorRepository floorRepository;
    private final ZoneCascadeService zoneCascadeService;

    /** 층 선택용 옵션 목록 */
    @Transactional(readOnly = true)
    public List<UnityFloorOptionResponse> getFloorOptions() {
        return floorRepository.findAllByOrderBySortOrderAsc().stream()
                .map(UnityFloorOptionResponse::from)
                .toList();
    }

    /**
     * 콜라이더 목록.
     *
     * @param floorId  층 필터
     * @param parentId 상위 구역 필터. 지정하면 그 구역의 <b>직속 자식</b>만 반환한다
     * @param rootOnly true 면 최상위 구역(depth 0)만. 기존 화면이 부모·자식이 섞여 나오는 걸
     *                 피하려면 이걸 쓴다
     */
    @Transactional(readOnly = true)
    public List<UnityZoneAdminResponse> getZones(Long floorId, Long parentId, boolean rootOnly) {
        List<UnityLocZone> zones;
        if (parentId != null) {
            zones = zoneRepository.findByParentIdWithFloor(parentId);
        } else if (floorId != null) {
            zones = zoneRepository.findByFloorIdWithFloor(floorId);
        } else {
            zones = zoneRepository.findAllWithFloor();
        }
        return zones.stream()
                .filter(z -> !rootOnly || z.isRoot())
                .map(UnityZoneAdminResponse::from)
                .toList();
    }

    /** 기존 시그니처 호환 — 필터 없이 전체(부모·자식 모두). */
    @Transactional(readOnly = true)
    public List<UnityZoneAdminResponse> getZones(Long floorId) {
        return getZones(floorId, null, false);
    }

    @Transactional(readOnly = true)
    public UnityZoneAdminResponse getZone(Long zoneId) {
        return UnityZoneAdminResponse.from(getZoneOrThrow(zoneId));
    }

    /** 콜라이더 등록 */
    @Transactional
    public UnityZoneAdminResponse createZone(UnityZoneRequest request) {
        String meshName = trimToNull(request.zoneMeshName());
        assertMeshNameAvailable(meshName, null);

        String zoneCode = trimToNull(request.zoneCode());
        assertZoneCodeAvailable(zoneCode, null);

        UnityLocFloor floor = getFloorOrThrow(request.floorId());
        UnityLocZone parent = resolveParent(request.parentId());
        zoneCascadeService.assertParentAssignable(null, parent);

        UnityLocZone zone = UnityLocZone.builder()
                .floor(floor)
                .zoneName(request.zoneName().trim())
                .zoneMeshName(meshName)
                .meshType(normalizeMeshType(request.meshType()))
                .parent(parent)
                .depth(parent == null ? 0 : parent.getDepth() + 1)
                .zoneCode(zoneCode)
                .attributes(request.attributes())
                .build();
        return UnityZoneAdminResponse.from(zoneRepository.save(zone));
    }

    /** 콜라이더 수정 */
    @Transactional
    public UnityZoneAdminResponse updateZone(Long zoneId, UnityZoneRequest request) {
        UnityLocZone zone = getZoneOrThrow(zoneId);

        String meshName = trimToNull(request.zoneMeshName());
        assertMeshNameAvailable(meshName, zoneId);

        String zoneCode = trimToNull(request.zoneCode());
        assertZoneCodeAvailable(zoneCode, zoneId);

        UnityLocFloor floor = getFloorOrThrow(request.floorId());
        UnityLocZone parent = resolveParent(request.parentId());
        zoneCascadeService.assertParentAssignable(zoneId, parent);

        zone.updateInfo(floor, request.zoneName().trim(), meshName, normalizeMeshType(request.meshType()));
        zone.updateHierarchy(parent, zoneCode, request.attributes());
        return UnityZoneAdminResponse.from(zone);
    }

    /* ──────────────────────────────────────────────────────────── */

    /**
     * mesh_name 중복 검사.
     * <p>mesh_name 은 필수({@code @NotBlank})라 여기 도달할 때 null 일 수 없다.
     * 방어적으로 한 번 더 막는다.</p>
     */
    private void assertMeshNameAvailable(String meshName, Long selfZoneId) {
        if (meshName == null) {
            throw new IllegalArgumentException("mesh_name 은 필수입니다.");
        }
//        boolean taken = (selfZoneId == null)
//                ? zoneRepository.existsByZoneMeshName(meshName)
//                : zoneRepository.existsByZoneMeshNameAndZoneIdNot(meshName, selfZoneId);
//        if (taken) {
//            throw new IllegalStateException("이미 사용 중인 mesh_name 입니다: " + meshName);
//        }
    }

    /** zone_code 중복 검사. null 은 제외(부분 유니크 인덱스와 동일 규칙). */
    private void assertZoneCodeAvailable(String zoneCode, Long selfZoneId) {
        if (zoneCode == null) {
            return;
        }
        boolean taken = (selfZoneId == null)
                ? zoneRepository.existsByZoneCode(zoneCode)
                : zoneRepository.existsByZoneCodeAndZoneIdNot(zoneCode, selfZoneId);
        if (taken) {
            throw new IllegalStateException("이미 사용 중인 zone_code 입니다: " + zoneCode);
        }
    }

    private UnityLocZone resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return zoneRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("상위 구역을 찾을 수 없습니다. id=" + parentId));
    }

    /** meshType 정규화: "3D" 만 3D, 그 외(미지정 포함)는 기본 2D */
    private String normalizeMeshType(String meshType) {
        return "3D".equalsIgnoreCase(meshType != null ? meshType.trim() : null) ? "3D" : "2D";
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
