package com.mpole.hana.digitaltwin.deviceGroup.dto;

import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import lombok.Builder;

import java.util.List;

/**
 * 그룹에 넣을 수 있는 후보 장비.
 *
 * <p>{@code suggestedRole} 은 카테고리로 추정한 역할이다. 화면이 기본값으로 채워주면
 * 부스 3대를 넣을 때 매번 고를 필요가 없다.</p>
 *
 * <p>{@code memberOf} 는 이 장비가 <b>이미 속해 있는 다른 그룹</b>의 코드 목록이다.
 * 배타성 제약을 뺐기 때문에 중복 배정이 막히지 않는다 — 롤스크린 모터가 "3층 전체"와
 * "3층 서쪽"에 동시에 속하는 것은 정상이고, 재실센서가 두 부스에 들어가는 것은 사고다.
 * 둘을 DB 가 구분할 수 없으니 <b>사람이 보고 판단하도록</b> 보여준다.</p>
 */
@Builder
public record DeviceGroupCandidateResponse(
        Long deviceId,
        String deviceKey,
        String deviceName,
        String deviceDisplayName,
        Long categoryId,
        String categoryCode,
        String categoryName,
        String suggestedRole,
        List<String> memberOf
) {
    public static DeviceGroupCandidateResponse from(Device device, String suggestedRole,
                                                    List<String> memberOf) {
        DeviceCategory category = device.getCategory();
        return DeviceGroupCandidateResponse.builder()
                .deviceId(device.getDeviceId())
                .deviceKey(device.getDeviceKey())
                .deviceName(device.getDeviceName())
                .deviceDisplayName(device.getDeviceDisplayName())
                .categoryId(category != null ? category.getCategoryId() : null)
                .categoryCode(category != null ? category.getCategoryCode() : null)
                .categoryName(category != null ? category.getCategoryName() : null)
                .suggestedRole(suggestedRole)
                .memberOf(memberOf != null ? memberOf : List.of())
                .build();
    }
}
