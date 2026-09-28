package com.mpole.hana.digitaltwin.deviceGroup.dto;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroupMember;
import com.mpole.hana.digitaltwin.deviceGroup.entity.DeviceGroupRole;
import lombok.Builder;

/**
 * 그룹에 속한 장비 한 대.
 *
 * <p>{@code role} 은 이 그룹에서 하는 일({@code CONTROLLER}·{@code MONITOR})이고,
 * {@code categoryCode} 는 장비가 무엇인지(조명·센서·모터)를 말한다. 둘은 다른 질문이다.</p>
 */
@Builder
public record DeviceGroupMemberResponse(
        Long memberId,
        Long deviceId,
        String deviceKey,
        String deviceName,
        String deviceDisplayName,
        Long categoryId,
        String categoryCode,
        String categoryName,
        String role,
        String roleLabel,
        int sortOrder
) {
    public static DeviceGroupMemberResponse from(DeviceGroupMember member) {
        Device device = member.getDevice();
        DeviceCategory category = device.getCategory();
        return DeviceGroupMemberResponse.builder()
                .memberId(member.getMemberId())
                .deviceId(device.getDeviceId())
                .deviceKey(device.getDeviceKey())
                .deviceName(device.getDeviceName())
                .deviceDisplayName(device.getDeviceDisplayName())
                .categoryId(category != null ? category.getCategoryId() : null)
                .categoryCode(category != null ? category.getCategoryCode() : null)
                .categoryName(category != null ? category.getCategoryName() : null)
                .role(member.getRole())
                // 알려진 값이면 한글 라벨, 모르는 값이면 코드 그대로 — 화면이 새 값도 표시할 수 있게.
                .roleLabel(DeviceGroupRole.labelOf(member.getRole()))
                .sortOrder(member.getSortOrder())
                .build();
    }
}
