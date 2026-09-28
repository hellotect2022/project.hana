package com.mpole.hana.digitaltwin.deviceGroup.service;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.deviceGroup.dto.*;
import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroup;
import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroupMember;
import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroupRole;
import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroupType;
import com.mpole.hana.digitaltwin.deviceGroup.repository.DeviceGroupCandidateRepository;
import com.mpole.hana.digitaltwin.deviceGroup.repository.DeviceGroupMemberRepository;
import com.mpole.hana.digitaltwin.deviceGroup.repository.DeviceGroupRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 장비 논리 그룹 관리.
 *
 * <p>기존 화장실·롤스크린 조회 로직은 건드리지 않는다. 지금은 그룹 구조를 나란히 세워
 * 대조하는 단계이고, 조회 전환은 대조가 끝난 뒤 별건으로 한다.</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceGroupService {

    /** 후보 목록 상한. 장비 8천대 전건을 화면에 쏟지 않기 위한 방어선. */
    private static final int CANDIDATE_LIMIT = 300;

    /**
     * 카테고리 → 기본 역할.
     *
     * <p>추정일 뿐이고 요청이 역할을 명시하면 그쪽이 이긴다. 여기 없는 카테고리는
     * 역할 없이 들어가며, 그래도 그룹 멤버로서는 유효하다.</p>
     */
    private static final Map<String, String> DEFAULT_ROLE_BY_CATEGORY = Map.of(
            "MRS-CONTROL", DeviceGroupRole.CONTROLLER.name(),   // 롤스크린 구역 제어기
            "RL",          DeviceGroupRole.CONTROLLER.name(),   // 화장실 조명 — 제어 대상
            "MRS",         DeviceGroupRole.MONITOR.name(),      // 롤스크린 모터 — pos-st 원본
            "OCC",         DeviceGroupRole.MONITOR.name(),      // 재실센서 — occ 원본
            "TB",          DeviceGroupRole.MONITOR.name()       // 물리버튼 — 눌림 이벤트 원본
    );

    private final DeviceGroupRepository deviceGroupRepository;
    private final DeviceGroupMemberRepository deviceGroupMemberRepository;
    private final DeviceGroupCandidateRepository deviceGroupCandidateRepository;

    // ────────────────────────────── 조회 ──────────────────────────────

    /** 화면 select 용 코드 목록. enum 정의값 + DB 에 실제로 쓰이는 값을 합친다. */
    @Transactional(readOnly = true)
    public DeviceGroupMetaResponse getMeta() {
        return DeviceGroupMetaResponse.of(deviceGroupRepository.findDistinctGroupTypes());
    }

    /**
     * 그룹 목록.
     *
     * <p>멤버수는 그룹별 count 쿼리 대신 한 번에 모아 온다 — 롤스크린 286건에서
     * 그대로 N+1 이 되기 때문이다.</p>
     */
    @Transactional(readOnly = true)
    public Page<DeviceGroupResponse> getGroups(String groupType, String keyword,
                                               Boolean active, Pageable pageable) {
        String type = DeviceGroupType.normalize(groupType);
        String like = toLikePattern(keyword);
        List<Boolean> actives = toActiveFilter(active);

        Page<DeviceGroup> groups = (type == null)
                ? deviceGroupRepository.search(like, actives, pageable)
                : deviceGroupRepository.searchByType(type, like, actives, pageable);

        Map<Long, Long> counts = countMembers(groups.getContent());
        return groups.map(g -> DeviceGroupResponse.summary(g, counts.getOrDefault(g.getGroupId(), 0L)));
    }

    @Transactional(readOnly = true)
    public DeviceGroupResponse getGroup(Long groupId) {
        DeviceGroup group = find(groupId);
        return DeviceGroupResponse.detail(group, getMembers(groupId));
    }

    /** 외부(HQS)는 숫자 id 가 아니라 group_code 로 그룹을 지목한다. */
    @Transactional(readOnly = true)
    public DeviceGroupResponse getGroupByCode(String groupCode) {
        DeviceGroup group = deviceGroupRepository.findByGroupCode(groupCode)
                .orElseThrow(() -> new EntityNotFoundException("장비 그룹을 찾을 수 없습니다. groupCode=" + groupCode));
        return DeviceGroupResponse.detail(group, getMembers(group.getGroupId()));
    }

    @Transactional(readOnly = true)
    public List<DeviceGroupMemberResponse> getMembers(Long groupId) {
        return deviceGroupMemberRepository.findByGroupIdWithDevice(groupId).stream()
                .map(DeviceGroupMemberResponse::from)
                .toList();
    }

    /**
     * 그룹에 넣을 후보 장비.
     *
     * <p>빼는 것은 <b>이미 그 그룹에 들어 있는 장비</b>뿐이다. 다른 그룹 소속은 빼지 않는다 —
     * 모터 한 대가 "3층 전체"와 "3층 서쪽"에 동시에 속하는 것이 정상이기 때문이다.</p>
     *
     * <p>대신 기존 소속 그룹({@code memberOf})을 함께 실어 보낸다. 재실센서가 두 부스에
     * 들어가는 것은 사고인데 DB 는 그 둘을 구분할 수 없으니, 사람이 보고 판단하게 한다.</p>
     */
    @Transactional(readOnly = true)
    public List<DeviceGroupCandidateResponse> getCandidates(Long groupId, Long categoryId,
                                                            String keyword, Integer limit) {
        find(groupId);      // 없는 그룹이면 여기서 404 성격의 예외
        String like = toLikePattern(keyword);
        Pageable page = PageRequest.of(0, clampLimit(limit));

        List<Device> devices = (categoryId == null)
                ? deviceGroupCandidateRepository.findCandidates(groupId, like, page)
                : deviceGroupCandidateRepository.findCandidatesByCategory(groupId, categoryId, like, page);

        Map<Long, List<String>> memberships = findMemberships(devices, groupId);

        return devices.stream()
                .map(d -> DeviceGroupCandidateResponse.from(
                        d, suggestRole(d), memberships.getOrDefault(d.getDeviceId(), List.of())))
                .toList();
    }

    // ────────────────────────────── 그룹 CRUD ──────────────────────────────

    @Transactional
    public DeviceGroupResponse createGroup(DeviceGroupRequest request) {
        String groupCode = request.groupCode();
        String groupType = DeviceGroupType.normalize(request.groupType());
        if (groupType == null) {
            throw new IllegalArgumentException("groupType 은 필수입니다.");
        }
        if (deviceGroupRepository.existsByGroupCode(groupCode)) {
            throw new IllegalStateException("이미 존재하는 groupCode 입니다: " + groupCode);
        }

        DeviceGroup group = DeviceGroup.builder()
                .groupCode(groupCode)
                .groupName(request.groupName())
                .groupType(groupType)
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .attributes(request.attributes())
                .active(request.active() == null || request.active())
                .description(request.description())
                .build();

        return DeviceGroupResponse.detail(deviceGroupRepository.save(group), List.of());
    }

    /**
     * 그룹 수정.
     *
     * <p>{@code groupCode} 는 바꿀 수 있다. 숫자 PK 로 전환한 덕분에 코드 변경이
     * <b>그룹 한 행 UPDATE</b> 로 끝나고 멤버 행은 건드리지 않는다. 다만 외부 연동
     * (HQS {@code wcBoothId})이 이 값을 식별자로 쓰므로 바꾸면 상대 시스템이 보는
     * 식별자가 바뀐다 — 화면에서 그 사실을 알려준다.</p>
     *
     * <p>{@code groupType} 은 바꾸지 않는다. 용도를 바꾸면 이미 배정된 장비가 엉뚱한 곳에
     * 남는다. 그룹을 새로 만들어 옮기는 것이 맞다.</p>
     */
    @Transactional
    public DeviceGroupResponse updateGroup(Long groupId, DeviceGroupRequest request) {
        DeviceGroup group = find(groupId);

        String requestedType = DeviceGroupType.normalize(request.groupType());
        if (requestedType != null && !requestedType.equals(group.getGroupType())) {
            throw new IllegalArgumentException(
                    "groupType 은 변경할 수 없습니다. 현재=" + group.getGroupType()
                            + ", 요청=" + requestedType + ". 새 그룹을 만들어 장비를 옮기세요.");
        }

        String newCode = request.groupCode();
        if (newCode != null && !newCode.equals(group.getGroupCode())) {
            // 제약에 맡기면 "이미 존재하는 값입니다" 뿐이라 어느 그룹과 부딪혔는지 알 수 없다.
            if (deviceGroupRepository.existsByGroupCode(newCode)) {
                throw new IllegalStateException("이미 존재하는 groupCode 입니다: " + newCode);
            }
            group.changeGroupCode(newCode);
        }

        group.update(request.groupName(), request.sortOrder(), request.attributes(),
                request.active(), request.description());

        return DeviceGroupResponse.detail(group, getMembers(groupId));
    }

    /** 그룹 삭제. 멤버는 DB {@code ON DELETE CASCADE} 로 함께 사라진다(장비 자체는 무관). */
    @Transactional
    public void deleteGroup(Long groupId) {
        deviceGroupRepository.delete(find(groupId));
    }

    // ────────────────────────────── 멤버 전체 교체 ──────────────────────────────

    /**
     * 멤버 <b>전체 교체</b>.
     *
     * <p>개별 add/remove 대신 목록을 통째로 받는다 — CCTV 매핑이 쓰는 기존 관례이고,
     * {@code sortOrder} 를 배열 인덱스로 다시 부여할 수 있어 번호가 비거나 겹치지 않는다.</p>
     *
     * <p>빈 배열이면 전체 해제다. 그룹은 남는다 — 장비 0대인 그룹은 유효하다.</p>
     *
     * <p>다른 그룹 소속은 검사하지 않는다. 배타성 제약을 뺐기 때문이고, 같은 장비가
     * 여러 그룹에 속하는 것이 정상인 경우가 있기 때문이다(롤스크린 모터).</p>
     */
    @Transactional
    public List<DeviceGroupMemberResponse> replaceMembers(Long groupId, DeviceGroupMemberBulkRequest request) {
        DeviceGroup group = find(groupId);
        List<DeviceGroupMemberBulkRequest.Item> items = request.devices();

        Set<Long> unique = new LinkedHashSet<>();
        for (DeviceGroupMemberBulkRequest.Item item : items) {
            if (!unique.add(item.deviceId())) {
                // uk_dgm_group_device 가 막아주지만, 메시지를 알아볼 수 있게 먼저 잡는다.
                throw new IllegalArgumentException("같은 장비가 두 번 들어 있습니다. deviceId=" + item.deviceId());
            }
        }

        deviceGroupMemberRepository.deleteByGroupId(groupId);
        deviceGroupMemberRepository.flush();

        if (items.isEmpty()) {
            return List.of();
        }

        Map<Long, Device> deviceById = deviceGroupCandidateRepository.findAllByIds(unique).stream()
                .collect(Collectors.toMap(Device::getDeviceId, d -> d));

        List<DeviceGroupMember> rows = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            DeviceGroupMemberBulkRequest.Item item = items.get(i);
            Device device = deviceById.get(item.deviceId());
            if (device == null) {
                throw new EntityNotFoundException("장비를 찾을 수 없습니다. deviceId=" + item.deviceId());
            }

            // 명시값이 있으면 그것을 쓰고(모르는 값도 그대로), 없으면 카테고리에서 추정한다.
            String role = DeviceGroupRole.normalize(item.role());
            if (role == null) {
                role = suggestRole(device);
            }

            rows.add(DeviceGroupMember.builder()
                    .group(group)
                    .device(device)
                    .role(role)
                    .sortOrder(i)                     // 화면에 보이는 순서를 0부터 다시 부여
                    .build());
        }

        deviceGroupMemberRepository.saveAll(rows);
        return getMembers(groupId);
    }

    // ────────────────────────────── 내부 ──────────────────────────────

    private DeviceGroup find(Long groupId) {
        return deviceGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("장비 그룹을 찾을 수 없습니다. groupId=" + groupId));
    }

    /** 카테고리로 역할을 추정한다. 화면이 기본값을 채워주면 부스 3대를 넣을 때 매번 고르지 않아도 된다. */
    private String suggestRole(Device device) {
        DeviceCategory category = device.getCategory();
        if (category == null || category.getCategoryCode() == null) {
            return null;
        }
        return DEFAULT_ROLE_BY_CATEGORY.get(category.getCategoryCode().toUpperCase(Locale.ROOT));
    }

    /** 후보 장비들이 이미 속한 다른 그룹 코드 — 화면에 경고로 보여주기 위한 것. */
    private Map<Long, List<String>> findMemberships(List<Device> devices, Long excludeGroupId) {
        if (devices.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = devices.stream().map(Device::getDeviceId).toList();
        Map<Long, List<String>> result = new HashMap<>();
        for (Object[] row : deviceGroupMemberRepository.findOtherMemberships(ids, excludeGroupId)) {
            result.computeIfAbsent(((Number) row[0]).longValue(), k -> new ArrayList<>())
                  .add((String) row[1]);
        }
        return result;
    }

    private Map<Long, Long> countMembers(List<DeviceGroup> groups) {
        if (groups.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = groups.stream().map(DeviceGroup::getGroupId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : deviceGroupMemberRepository.countByGroupIds(ids)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    /**
     * 활성 선택필터를 <b>값 목록</b>으로 바꾼다.
     *
     * <p>"전체"를 {@code null} 로 넘겨 {@code :active IS NULL} 로 푸는 대신
     * {@code [true, false]} 를 넘긴다 — 파라미터가 항상 값을 가지므로 PostgreSQL
     * 타입추론 문제가 생길 여지 자체가 없다.</p>
     */
    private static List<Boolean> toActiveFilter(Boolean active) {
        if (active == null) {
            return List.of(Boolean.TRUE, Boolean.FALSE);
        }
        return List.of(active);
    }

    /** 빈 keyword 는 {@code %} 가 되어 전건을 통과한다. {@code :param IS NULL} 패턴을 쓰지 않기 위한 장치. */
    private static String toLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "%";
        }
        return "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return CANDIDATE_LIMIT;
        }
        return Math.min(limit, CANDIDATE_LIMIT);
    }
}
