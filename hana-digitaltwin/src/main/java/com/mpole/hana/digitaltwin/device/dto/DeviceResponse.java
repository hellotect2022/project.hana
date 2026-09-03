package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.common.constant.ConnectionState;
import com.mpole.hana.digitaltwin.device.entity.Device;
import com.mpole.hana.digitaltwin.device.entity.DeviceCategory;
import com.mpole.hana.digitaltwin.device.entity.DevicePlacement;
import com.mpole.hana.digitaltwin.device.entity.OperationState;
import com.mpole.hana.digitaltwin.realtime.dto.DeviceRealtimeResponse;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;
import com.mpole.hana.digitaltwin.unityAsset.entity.UnityAsset;
import com.mpole.hana.digitaltwin.device.property.DevicePropertyInfo;
import com.mpole.hana.digitaltwin.device.property.CctvProperty;
import com.mpole.hana.digitaltwin.device.property.CctvPropertyResponse;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public record DeviceResponse(
        Long deviceId,
        String deviceName,
        String deviceDisplayName,
        String description,
        Boolean active,
        Long categoryId,
        String categoryName,
        String categoryPath,
        Long assetId,          // category.defaultAsset
        String assetName,
        DeviceTransformResponse transform,
        DeviceLocationResponse location,
        boolean placed,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        DevicePropertyInfo propertyInfo,
        DeviceRealtimeResponse currentValue,
        List<CommandPointItem> commandPoints,
        boolean alarmState,
        OperationState operationState,
        ConnectionState connectionState,
        Long infraId
) {
    /**
     * 디바이스 목록/상세에 임베드되는 제어 포인트 요약.
     * <p>제어(command) 도메인과의 순환 의존을 피하기 위해 원시 필드만 담고,
     * DeviceService 가 배치 조회 결과로 조립한다.</p>
     */
    public record CommandPointItem(
            Long commandPointId,
            Long pointId,
            String label,
            String tagName,
            String onValue,
            String offValue
    ) {
    }

    public static DeviceResponse from(Device d) {
        return from(d, List.of());
    }

    /** commandPoints 는 서비스에서 배치 조회로 채워 전달한다(뷰어/프론트 미사용 시 무시되므로 안전). */
    public static DeviceResponse from(Device d, List<CommandPointItem> commandPoints) {
        if (d==null) return null;
        DeviceCategory cat = d.getCategory();
        UnityAsset asset = cat != null ? cat.getDefaultAsset() : null;
        DevicePlacement p = d.getPlacement();
        DeviceCurrentValue c = d.getDeviceCurrentValue();

        // property_info: CCTV 는 응답에서만 stream_url 을 계산해 포함(미저장, 타입은 DevicePropertyInfo 유지).
        DevicePropertyInfo pi = d.getPropertyInfo();
        DevicePropertyInfo propertyInfoOut = (pi instanceof CctvProperty cp) ? CctvPropertyResponse.from(cp) : pi;

        return new DeviceResponse(
                d.getDeviceId(),
                d.getDeviceName(),
                d.getDeviceDisplayName(),
                d.getDescription(),
                d.getActive(),
                cat != null ? cat.getCategoryId() : null,
                cat != null ? cat.getCategoryName() : null,
                buildCategoryPath(cat),
                asset != null ? asset.getAssetId() : null,
                asset != null ? asset.getAssetName() : null,
                DeviceTransformResponse.from(p),
                DeviceLocationResponse.from(p),
                p != null && p.isPlaced(),
                d.getCreatedAt(),
                d.getUpdatedAt()
                , propertyInfoOut
                , DeviceRealtimeResponse.from(c)
                , commandPoints != null ? commandPoints : List.of()
                , d.isAlarmState()
                , d.getOperationState()
                // v2.1: 통신상태 소스 = current_value(상태 테이블). 행/값 없으면 UNKNOWN.
                , c != null && c.getConnectionState() != null ? c.getConnectionState() : ConnectionState.UNKNOWN
                , d.getInfraId()
        );
    }

    public static String buildCategoryPath(DeviceCategory entity) {
        List<String> path = new ArrayList<>();
        DeviceCategory current = entity;
        while (current != null) {
            path.add(current.getCategoryName());
            current = current.getParent();
        }
        return String.join(" > ", path.reversed());
    }
}
