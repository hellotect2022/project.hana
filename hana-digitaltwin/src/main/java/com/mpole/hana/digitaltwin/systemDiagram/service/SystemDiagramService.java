package com.mpole.hana.digitaltwin.systemDiagram.service;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.repository.DeviceRepository;
import com.mpole.hana.digitaltwin.systemDiagram.dto.SystemDiagramRequest;
import com.mpole.hana.digitaltwin.systemDiagram.dto.SystemDiagramResponse;
import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagram;
import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagramDevice;
import com.mpole.hana.digitaltwin.systemDiagram.entity.SystemDiagramWiring;
import com.mpole.hana.digitaltwin.systemDiagram.entity.WiringSegment;
import com.mpole.hana.digitaltwin.systemDiagram.repository.SystemDiagramRepository;
import com.mpole.hana.digitaltwin.unityAsset.entity.AssetType;
import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import com.mpole.hana.digitaltwin.unityAsset.repository.UnityAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SystemDiagramService {

    private final SystemDiagramRepository systemDiagramRepository;
    private final DeviceRepository deviceRepository;
    private final UnityAssetRepository unityAssetRepository;

    public SystemDiagramResponse create(SystemDiagramRequest req) {
        validateBasic(req);

        Device master = loadDevice(req.masterDeviceId(), "마스터 장비(masterDeviceId)");

        SystemDiagram diagram = SystemDiagram.builder()
                .diagramName(req.diagramName())
                .diagramCode(normalizeCode(req.diagramCode()))
                .description(req.description())
                .masterDevice(master)
                .sortOrder(req.sortOrder())
                .active(req.active() != null ? req.active() : Boolean.TRUE)
                .build();

        applyChildren(diagram, master, req);

        SystemDiagram saved = systemDiagramRepository.save(diagram);
        log.info("계통도 생성: diagramId={}, name={}", saved.getDiagramId(), saved.getDiagramName());
        return SystemDiagramResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SystemDiagramResponse> list() {
        return systemDiagramRepository.findAllByOrderBySortOrderAscDiagramIdDesc().stream()
                .map(SystemDiagramResponse::from)
                .toList();
    }

    /** 계통도 목록 페이지 조회. keyword(계통도명/코드) 부분일치 검색(대소문자 무시). */
    @Transactional(readOnly = true)
    public Page<SystemDiagramResponse> listPaged(String keyword, Pageable pageable) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        // null 파라미터를 쿼리 함수 인자로 넘기면 Postgres 가 타입 추론 실패(lower(bytea)) → 분기 처리.
        Page<SystemDiagram> page = hasKeyword
                ? systemDiagramRepository.search(keyword.trim(), pageable)
                : systemDiagramRepository.findAll(pageable);
        return page.map(SystemDiagramResponse::from);
    }

    @Transactional(readOnly = true)
    public SystemDiagramResponse get(Long id) {
        return SystemDiagramResponse.from(findOrThrow(id));
    }

    public SystemDiagramResponse update(Long id, SystemDiagramRequest req) {
        validateBasic(req);

        SystemDiagram diagram = findOrThrow(id);
        Device master = loadDevice(req.masterDeviceId(), "마스터 장비(masterDeviceId)");

        diagram.setDiagramName(req.diagramName());
        diagram.setDiagramCode(normalizeCode(req.diagramCode()));
        diagram.setDescription(req.description());
        diagram.setMasterDevice(master);
        diagram.setSortOrder(req.sortOrder());
        diagram.setActive(req.active() != null ? req.active() : Boolean.TRUE);

        // devices / wirings 전체 교체 (orphanRemoval 로 기존 하위 삭제)
        diagram.clearDevices();
        diagram.clearWirings();
        // JPA(Hibernate)는 트랜잭션이 끝나거나 flush될 때 INSERT -> UPDATE -> DELETE 순서로 SQL을 실행하는 기본 전략을 가집니다.
        // 아래 코드가 없으면 새로운값을 insert 를 먼저 하게 됨
        systemDiagramRepository.flush(); // 또는 entityManager.flush();

        applyChildren(diagram, master, req);

        SystemDiagram saved = systemDiagramRepository.save(diagram);
        log.info("계통도 수정: diagramId={}", saved.getDiagramId());
        return SystemDiagramResponse.from(saved);
    }

    public void delete(Long id) {
        SystemDiagram diagram = findOrThrow(id);
        systemDiagramRepository.delete(diagram);
        log.info("계통도 삭제: diagramId={}", id);
    }

    // ------------------------------------------------------------------
    // 내부 로직
    // ------------------------------------------------------------------

    private SystemDiagram findOrThrow(Long id) {
        return systemDiagramRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("계통도를 찾을 수 없습니다. id=" + id));
    }

    /**
     * 소속 서브 장비(devices)와 배선(wirings)을 조립한다.
     * from/to/서브 device 가 계통도 소속(master 또는 devices 목록)인지 검증하고,
     * 각 segment.assetId 가 존재하고 asset_type=PIPE 인지 검증한다.
     */
    private void applyChildren(SystemDiagram diagram, Device master, SystemDiagramRequest req) {
        // 계통도 소속 device id 집합 = master + devices 목록
        Set<Long> memberDeviceIds = new HashSet<>();
        memberDeviceIds.add(master.getDeviceId());

        if (req.devices() != null) {
            for (SystemDiagramRequest.DeviceItem item : req.devices()) {
                if (item.deviceId() == null) {
                    throw new IllegalArgumentException("서브 장비 항목의 deviceId 는 필수입니다.");
                }
                Device device = loadDevice(item.deviceId(), "서브 장비(deviceId)");
                SystemDiagramDevice mapping = SystemDiagramDevice.builder()
                        .device(device)
                        .sortOrder(item.sortOrder())
                        .build();
                diagram.addDevice(mapping);
                memberDeviceIds.add(device.getDeviceId());
            }
        }

        if (req.wirings() != null) {
            for (SystemDiagramRequest.WiringItem item : req.wirings()) {
                if (item.wiringName() == null || item.wiringName().isBlank()) {
                    throw new IllegalArgumentException("배선명(wiringName)은 필수입니다.");
                }
                Device from = loadDevice(item.fromDeviceId(), "배선 시작 장비(fromDeviceId)");
                Device to = loadDevice(item.toDeviceId(), "배선 끝 장비(toDeviceId)");

                if (!memberDeviceIds.contains(from.getDeviceId())) {
                    throw new IllegalArgumentException(
                            "배선 시작 장비(fromDeviceId=" + from.getDeviceId() + ")가 계통도 소속 장비가 아닙니다.");
                }
                if (!memberDeviceIds.contains(to.getDeviceId())) {
                    throw new IllegalArgumentException(
                            "배선 끝 장비(toDeviceId=" + to.getDeviceId() + ")가 계통도 소속 장비가 아닙니다.");
                }

                SystemDiagramWiring wiring = SystemDiagramWiring.builder()
                        .wiringName(item.wiringName())
                        .wiringType(item.wiringType())
                        .color(item.color())
                        .sortOrder(item.sortOrder())
                        .fromDevice(from)
                        .toDevice(to)
                        .segments(buildSegments(item.segments()))
                        .build();
                diagram.addWiring(wiring);
            }
        }
    }

    private List<WiringSegment> buildSegments(List<SystemDiagramRequest.SegmentItem> items) {
        List<WiringSegment> segments = new ArrayList<>();
        if (items == null) return segments;
        for (SystemDiagramRequest.SegmentItem item : items) {
            validatePipeAsset(item.assetId());
            segments.add(new WiringSegment(
                    item.assetId(),
                    toPoint3(item.start()),
                    toPoint3(item.end())
            ));
        }
        return segments;
    }

    private WiringSegment.Point3 toPoint3(SystemDiagramRequest.PointItem p) {
        if (p == null) return null;
        return new WiringSegment.Point3(p.x(), p.y(), p.z());
    }

    /** segment.assetId 는 DB FK 로 강제할 수 없으므로 서비스에서 존재 + asset_type=PIPE 를 검증한다. */
    private void validatePipeAsset(Long assetId) {
        if (assetId == null) {
            throw new IllegalArgumentException("배선 세그먼트의 assetId 는 필수입니다.");
        }
        UnityAsset asset = unityAssetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("배관 에셋을 찾을 수 없습니다. assetId=" + assetId));
        if (asset.getAssetType() != AssetType.PIPE) {
            throw new IllegalArgumentException(
                    "세그먼트 assetId=" + assetId + " 는 배관(PIPE) 에셋이 아닙니다. (asset_type="
                            + (asset.getAssetType() != null ? asset.getAssetType().name() : "null") + ")");
        }
    }

    private Device loadDevice(Long deviceId, String label) {
        if (deviceId == null) {
            throw new IllegalArgumentException(label + " 는 필수입니다.");
        }
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException(label + " 를 찾을 수 없습니다. id=" + deviceId));
    }

    private String normalizeCode(String code) {
        if (code == null) return null;
        String trimmed = code.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateBasic(SystemDiagramRequest req) {
        if (req.diagramName() == null || req.diagramName().isBlank()) {
            throw new IllegalArgumentException("계통도명(diagramName)은 필수입니다.");
        }
        if (req.masterDeviceId() == null) {
            throw new IllegalArgumentException("마스터 장비(masterDeviceId)는 필수입니다.");
        }
    }
}
