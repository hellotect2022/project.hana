package com.mpole.hana.digitaltwin.deviceGroup.dto;

import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroup;
import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroupType;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 그룹 응답.
 *
 * <p>{@code members} 는 상세 조회에서만 채운다 — 목록에서는 {@code memberCount} 만 주고
 * 멤버 배열은 {@code null} 로 둔다. 롤스크린 286그룹의 멤버를 목록마다 실어보낼 이유가 없다.</p>
 */
@Builder
public record DeviceGroupResponse(
        Long groupId,
        String groupCode,
        String groupName,
        String groupType,
        String groupTypeLabel,
        int sortOrder,
        Map<String, Object> attributes,
        boolean active,
        String description,
        long memberCount,
        List<DeviceGroupMemberResponse> members,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** 목록용 — 멤버 배열 없이 개수만. */
    public static DeviceGroupResponse summary(DeviceGroup group, long memberCount) {
        return base(group).memberCount(memberCount).build();
    }

    /** 상세용 — 멤버 배열 포함. */
    public static DeviceGroupResponse detail(DeviceGroup group, List<DeviceGroupMemberResponse> members) {
        return base(group)
                .memberCount(members.size())
                .members(members)
                .build();
    }

    private static DeviceGroupResponseBuilder base(DeviceGroup group) {
        return DeviceGroupResponse.builder()
                .groupId(group.getGroupId())
                .groupCode(group.getGroupCode())
                .groupName(group.getGroupName())
                .groupType(group.getGroupType())
                // 알려진 값이면 한글 라벨, 모르는 값이면 코드 그대로 — 화면이 새 용도도 표시할 수 있게.
                .groupTypeLabel(DeviceGroupType.labelOf(group.getGroupType()))
                .sortOrder(group.getSortOrder())
                .attributes(group.getAttributes())
                .active(group.isActive())
                .description(group.getDescription())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt());
    }
}
