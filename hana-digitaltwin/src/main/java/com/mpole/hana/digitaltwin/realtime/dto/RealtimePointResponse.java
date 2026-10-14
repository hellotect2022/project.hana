package com.mpole.hana.digitaltwin.realtime.dto;

import com.mpole.hana.common.dto.XnPointData;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaIndex;

import java.time.OffsetDateTime;

/**
 * 실시간 현재값 1건 — <b>응답 전용</b> 타입.
 *
 * <h3>왜 XnPointData 를 그대로 안 쓰는가</h3>
 * <p>{@link XnPointData} 는 {@code hana-common} 의 공용 DTO 다. gateway → Kafka → event 로 흐르는
 * 메시지 포맷이자 {@code tbl_device_current_value.points} 에 <b>실제로 저장되는 형태</b>이기도 하다.
 * 거기에 {@code sortOrder}/{@code hud} 를 넣으면</p>
 * <ul>
 *   <li>gateway·event 까지 같이 재배포해야 하고,</li>
 *   <li>그 값이 JSONB 에 저장돼 <b>정본이 카테고리 스키마와 두 곳으로 갈라지며</b>
 *       (스키마를 고쳐도 저장된 값은 옛것), </li>
 *   <li>1초 주기 값 파이프라인의 페이로드가 태그 수만큼 커진다.</li>
 * </ul>
 * <p>값 파이프라인은 값만 나르고 메타는 응답 계층에서 붙인다 — 그래서 여기서 옮겨 담는다.</p>
 *
 * <p>필드 이름·순서는 기존 응답과 같게 두고 {@code sortOrder}/{@code hud} 두 개만 늘렸다.
 * 기존 클라이언트는 깨지지 않는다.</p>
 *
 * @param sortOrder 표시 순서(0부터). 스키마에 없는 태그는 null
 * @param hud       3D HUD 표출 여부. 미지정이면 false
 */
public record RealtimePointResponse(
        Long deviceId,
        Long pointId,
        String pointkey,
        String deviceCode,
        String pointCode,
        String alarmYn,
        String objectType,
        String pointDisplayName,
        String valueRaw,
        String unit,
        String tagName,
        OffsetDateTime updateDatetime,
        Integer sortOrder,
        Boolean hud
) {

    /**
     * @param schema 이 장비 카테고리의 스키마 색인. {@link PointSchemaIndex#empty()} 면
     *               {@code sortOrder=null · hud=false} 로 나간다
     */
    public static RealtimePointResponse from(XnPointData p, PointSchemaIndex schema) {
        if (p == null) {
            return null;
        }
        PointSchemaIndex idx = schema != null ? schema : PointSchemaIndex.empty();
        return new RealtimePointResponse(
                p.deviceId(),
                p.pointId(),
                p.pointkey(),
                p.deviceCode(),
                p.pointCode(),
                p.alarmYn(),
                p.objectType(),
                p.pointDisplayName(),
                p.valueRaw(),
                p.unit(),
                p.tagName(),
                p.updateDatetime(),
                idx.sortOrderOf(p.tagName()),
                idx.hudOf(p.tagName())
        );
    }
}
