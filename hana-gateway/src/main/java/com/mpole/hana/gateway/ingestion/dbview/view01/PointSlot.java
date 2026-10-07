package com.mpole.hana.gateway.ingestion.dbview.view01;

import com.mpole.hana.common.constant.PointDataSource;
import com.mpole.hana.common.constant.PointType;
import com.mpole.hana.common.dto.XnPointData;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * <b>포인트 한 칸(slot)</b> — "이 장비에 이 포인트가 있어야 한다"는 골격 + 현재 값.
 *
 * <p>메타(장비/포인트 식별자·태그·단위)는 {@code tbl_device_point} 에서 init 시 확정되어 불변이고,
 * 값(valueRaw/alarmYn/updateDatetime)만 폴링이 덮어쓴다.</p>
 *
 * <p><b>왜 가변 객체인가</b> — 매 사이클 뷰 행으로 새 객체를 만들어 그룹핑하면 초당 수십만 객체가
 * 생성·폐기된다. 골격을 한 번 만들어두고 값 칸만 갱신하면 할당이 사라진다.</p>
 *
 * <p><b>동시성</b> — 값 필드는 volatile. 쓰기는 폴링 스레드(들)가, 읽기는 번들 생성이 한다.
 * 하나의 slot 에 두 스레드가 동시에 쓰는 경우는 없다(뷰의 한 신호는 한 행으로만 온다).</p>
 */
public final class PointSlot {

    // ── 메타(불변) ─────────────────────────────
    private final Long deviceId;
    private final Long pointId;
    private final String pointKey;
    private final String refDeviceCode;
    private final String refPointCode;
    private final String tagName;
    private final String pointDisplayName;
    private final String unit;
    private final PointType pointType;
    /** 값의 원천 수집 경로. null 은 하위호환으로 VIEW01 취급({@link PointDataSource#isView01Fed}). */
    private final PointDataSource dataSource;

    // ── 값(가변) ───────────────────────────────
    private volatile String valueRaw;
    private volatile String alarmYn;
    private volatile OffsetDateTime updateDatetime;
    private static final OffsetDateTime UNCOLLECTED_AT = OffsetDateTime.of(1999, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(9));

    public PointSlot(Long deviceId, Long pointId, String pointKey,
                     String refDeviceCode, String refPointCode,
                     String tagName, String pointDisplayName, String unit, PointType pointType,
                     PointDataSource dataSource) {
        this.deviceId = deviceId;
        this.pointId = pointId;
        this.pointKey = pointKey;
        this.refDeviceCode = refDeviceCode;
        this.refPointCode = refPointCode;
        this.tagName = tagName;
        this.pointDisplayName = pointDisplayName;
        this.unit = unit;
        this.pointType = pointType;
        this.dataSource = dataSource;
        this.updateDatetime = OffsetDateTime.now();
    }

    /**
     * 뷰에서 온 값을 적용한다.
     *
     * <p>변경 판정은 <b>{@code valueRaw} + {@code alarmYn}</b> 기준이다 —
     * 소스가 값 변화 없이 {@code update_datetime} 만 touch 하는 경우를 걸러내기 위함
     * (기존 ChangeFilter 가 시각 기준이라 사실상 전량 통과하던 문제를 여기서 바로잡는다).</p>
     *
     * @return 값이 실제로 바뀌었으면 true (= 이 장비를 발행 대상으로 표시)
     */
    public boolean applyIfChanged(String newValueRaw, String newAlarmYn, OffsetDateTime newUpdateDatetime) {
        if (newUpdateDatetime != null && this.updateDatetime.isBefore(newUpdateDatetime)) {
            this.updateDatetime = newUpdateDatetime;
            this.valueRaw = newValueRaw;
            this.alarmYn = newAlarmYn;
            return true;
        }
        return false;
    }

    /** 아직 뷰에서 값을 한 번도 못 받은 칸인지. 정합성 진단용. */
    public boolean isUncollected() {
        return this.updateDatetime == null && this.valueRaw == null;
    }

    /** 번들에 실을 형태로 변환. 값이 없으면 valueRaw/updateDatetime 이 null 인 채로 나간다. */
    public XnPointData toXnPointData() {
        return XnPointData.builder()
                .deviceId(deviceId)
                .pointId(pointId)
                .pointkey(pointKey)
                .deviceCode(refDeviceCode)
                .pointCode(refPointCode)
                .alarmYn(alarmYn)
                .objectType(pointType != null ? pointType.name() : null)
                .pointDisplayName(pointDisplayName)
                .valueRaw(valueRaw)
                .unit(unit)
                .tagName(tagName)
                .updateDatetime(updateDatetime != null ? updateDatetime : UNCOLLECTED_AT)
                .build();
    }

    /** 메타 리로드 시 이전 slot 의 값을 이월한다(안 하면 리프레시마다 값이 전부 초기화된다). */
    public void carryValueFrom(PointSlot previous) {
        if (previous == null) return;
        this.valueRaw = previous.valueRaw;
        this.alarmYn = previous.alarmYn;
        this.updateDatetime = previous.updateDatetime;
    }

    public Long deviceId() {
        return deviceId;
    }

    public Long pointId() {
        return pointId;
    }

    public String pointKey() {
        return pointKey;
    }

    /** point_key 끝에 붙어 있는 태그명. device_key 를 되돌릴 때 이 길이만큼 뒤에서 뗀다. */
    public String tagName() {
        return tagName;
    }

    public OffsetDateTime updateDatetime() {
        return updateDatetime;
    }

    public PointDataSource dataSource() {
        return dataSource;
    }

    /** 이 뷰 스냅샷 파이프라인이 값을 공급하는 칸인가(null=미지정은 하위호환으로 true). */
    public boolean isViewFed() {
        return PointDataSource.isView01Fed(dataSource);
    }

    private static boolean equalsNullable(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
