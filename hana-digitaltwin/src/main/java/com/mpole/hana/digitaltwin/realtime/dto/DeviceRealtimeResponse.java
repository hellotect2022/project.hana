package com.mpole.hana.digitaltwin.realtime.dto;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 실시간 값 폴링 응답 — {@code POST /api/device-realtime} 전용.
 *
 * <p>이 API 는 {@code deviceId} 목록만 받아 {@code tbl_device_current_value} 를 읽는다.
 * 장비·카테고리를 모르므로 <b>포인트 메타(sortOrder·hud)를 붙일 수 없고, 붙이지도 않는다</b> —
 * 값만 나르는 경로다.</p>
 *
 * <p>메타가 필요한 장비 정보 응답은 {@link DeviceCurrentValueResponse} 를 쓴다.
 * 한 타입으로 합치고 모를 때 null 을 넣으면 같은 필드가 응답마다 있다 없다 하게 되어,
 * 클라이언트가 스펙만 보고는 판단할 수 없게 된다.</p>
 *
 * <p>클라이언트는 장비 정보를 한 번 받아 {@code tagName} 으로 메타를 캐시해 두고,
 * 이 응답의 값과 조인하면 된다({@code (device_id, tag_name)} 은 유일하다 —
 * {@code point_key} 유니크 제약이 그것을 보장한다).</p>
 */
public record DeviceRealtimeResponse(
        Long deviceId,
        OffsetDateTime timestamp,
        List<XnPointData> points
) {
    public static DeviceRealtimeResponse from(DeviceCurrentValue currentValue) {
        if (currentValue == null) return null;
        return new DeviceRealtimeResponse(
                currentValue.getDeviceId(),
                currentValue.getUpdatedAt(),
                currentValue.getPoints()
        );
    }

    private static DeviceRealtimeResponse empty() {
        return new DeviceRealtimeResponse(null, null, null);
    }
}
