package com.mpole.hana.gateway.ingestion.dbview.view01;

import com.mpole.hana.common.dto.DeviceBundle;
import com.mpole.hana.common.dto.XnPointData;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * <b>장비 한 대의 포인트 골격 전체.</b>
 *
 * <p>{@code tbl_device_point} 기준으로 "이 장비가 가져야 할 포인트"를 전부 들고 있다.
 * 발행 시에는 <b>값이 바뀐 포인트만이 아니라 이 slots 전체</b>를 번들에 싣는다 —
 * 하류(current_value)가 points 배열을 통째로 교체(REPLACE)하기 때문에,
 * 변경분만 보내면 안 바뀐 포인트가 DB 에서 사라진다.</p>
 */
public record DeviceSnapshot(
        Long deviceId,
        String deviceKey,
        List<PointSlot> slots
) {

    /**
     * 현재 값 상태로 번들을 만든다.
     *
     * <p>timestamp 는 slots 중 <b>가장 최신 non-null</b> 수집시각이며, 전부 비어 있으면
     * {@code fallback}(수집 시각)을 쓴다 — 번들 timestamp 가 null 로 나가면
     * 하류 정렬({@code Comparator.comparing})에서 NPE 가 난다.</p>
     */
    public DeviceBundle toBundle(OffsetDateTime fallback) {
        List<XnPointData> points = slots.stream()
                .map(PointSlot::toXnPointData)
                // 최신 수집시각 우선. 값이 없는(미수집) 포인트는 뒤로.
                .sorted(Comparator.comparing(XnPointData::updateDatetime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        OffsetDateTime latest = slots.stream()
                .map(PointSlot::updateDatetime)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(fallback);

        return DeviceBundle.builder()
                .deviceId(deviceId)
                .deviceKey(deviceKey)
                .timestamp(latest)
                .points(points)
                .build();
    }

    /** 아직 한 번도 값을 못 받은 포인트 수(정합성 진단용 — 스키마엔 있는데 뷰에 안 오는 포인트). */
    public long uncollectedCount() {
        return slots.stream().filter(PointSlot::isUncollected).count();
    }

    /**
     * 이 뷰 파이프라인이 값을 공급하는 포인트를 하나라도 가졌는가.
     *
     * <p>기동 시 전량 발행({@code markAllDirty})의 대상 판정에 쓴다. 뷰 신호가 하나도 없는 장비
     * (RabbitMQ 로 채우는 부스, 계산값만 있는 통계 장비 등)를 발행하면 값 없는 포인트가 그대로 나가
     * 하류에서 그 장비의 {@code points} 가 null 로 덮인다.</p>
     */
    public boolean hasViewFedPoint() {
        return slots.stream().anyMatch(PointSlot::isViewFed);
    }
}
