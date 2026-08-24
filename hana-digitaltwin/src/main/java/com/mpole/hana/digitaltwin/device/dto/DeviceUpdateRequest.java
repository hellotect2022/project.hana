package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.digitaltwin.device.entity.ConnectionState;
import com.mpole.hana.digitaltwin.device.entity.OperationState;
import com.mpole.hana.digitaltwin.device.property.DevicePropertyInfo;

import java.util.List;

public record DeviceUpdateRequest(
        String deviceName,
        // 장비 UI 표기명(선택). null=미변경. 비면 프론트에서 deviceName 으로 폴백.
        String deviceDisplayName,
        String deviceKey,
        String description,
        Boolean active,
        Long categoryId,
        // deprecated: 3D 에셋은 카테고리(defaultAsset) 기준으로 렌더한다. 필드는 하위호환용으로 남기되 사용하지 않음.
        Long assetId,
        // 장비 고유 속성(jsonb). type 판별자로 폴리모픽 역직렬화(CctvProperty 등). property-types 메타의 template 참고.
        DevicePropertyInfo propertyInfo,
        // 상태값(선택). alarm_state=false 면 알람 억제. operation/connection 은 산정 로직 추후.
        Boolean alarmState,
        OperationState operationState,
        ConnectionState connectionState,
        List<DevicePointRegisterItem> points
) {
}
