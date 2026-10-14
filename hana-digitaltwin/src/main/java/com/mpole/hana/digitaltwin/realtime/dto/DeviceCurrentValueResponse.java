package com.mpole.hana.digitaltwin.realtime.dto;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaIndex;
import com.mpole.hana.digitaltwin.realtime.entity.DeviceCurrentValue;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 장비 정보 응답에 실리는 현재값 — <b>스키마 메타가 붙은</b> 형태.
 *
 * <h3>{@link DeviceRealtimeResponse} 와 나눈 이유</h3>
 * <p>{@code sortOrder}/{@code hud} 는 카테고리 스키마에서 오는데, 그 스키마를 알 수 있는 곳과
 * 없는 곳이 갈린다.</p>
 * <pre>
 *   장비 정보 조회   Device → Category 를 이미 들고 있다  → 메타를 붙일 수 있다  → 이 타입
 *   실시간 폴링      deviceId 목록만 받는다               → 스키마를 모른다      → DeviceRealtimeResponse
 * </pre>
 *
 * <p>한 타입으로 두고 모를 때 null 을 넣으면 <b>같은 필드가 응답마다 있다 없다</b> 하게 된다.
 * 클라이언트는 왜 어떤 응답엔 값이 없는지 서버 코드를 봐야 알 수 있고, 스키마 문서에도
 * "있을 수도 없을 수도"로밖에 못 적는다. 타입을 나누면 그 차이가 계약에 드러나고,
 * <b>스키마를 모르는 자리에 이 타입을 쓰는 실수를 컴파일 단계에서 막는다.</b></p>
 */
public record DeviceCurrentValueResponse(
        Long deviceId,
        OffsetDateTime timestamp,
        List<RealtimePointResponse> points
) {

    /**
     * @param schema 이 장비 카테고리의 스키마 색인.
     *               {@link PointSchemaIndex#of(com.mpole.hana.digitaltwin.device.entity.DeviceCategory)}
     *               로 만들며 추가 쿼리가 들지 않는다(카테고리는 이미 로딩돼 있다)
     */
    public static DeviceCurrentValueResponse from(DeviceCurrentValue currentValue, PointSchemaIndex schema) {
        if (currentValue == null) {
            return null;
        }

        List<XnPointData> raw = currentValue.getPoints();
        // 저장된 JSONB 배열 순서는 "값이 갱신된 순서"라 표시 순서로 쓸 수 없다.
        // 스키마에 없는 태그(스키마에서 지워졌는데 값은 남은 것)는 뒤로 보낸다.
        List<RealtimePointResponse> points = raw == null ? null : raw.stream()
                .map(p -> RealtimePointResponse.from(p, schema))
                .sorted(Comparator.comparing(RealtimePointResponse::sortOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new DeviceCurrentValueResponse(
                currentValue.getDeviceId(),
                currentValue.getUpdatedAt(),
                points
        );
    }
}
