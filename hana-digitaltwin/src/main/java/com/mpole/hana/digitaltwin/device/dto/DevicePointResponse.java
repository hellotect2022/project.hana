package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.common.constant.PointDataSource;
import com.mpole.hana.digitaltwin.device.entity.DevicePoint;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;

/**
 * @param dataSource      값의 원천 수집 경로 코드. 미지정이면 null(= VIEW01 취급)
 * @param dataSourceLabel 화면 표시용 한글 라벨. 미지정이면 null
 * @param sortOrder       표시 순서(0부터). 카테고리 스키마에서 가져온다. 미지정이면 null
 * @param hud             3D HUD 표출 여부. 카테고리 스키마에서 가져온다. 미지정이면 false
 */
public record DevicePointResponse(
        Long pointId,
        String pointKey,
        String tagName,
        String description,
        String pointDisplayName,
        String pointType,
        String unit,
        Boolean active,
        String refDeviceCode,
        String refPointCode,
        String dataSource,
        String dataSourceLabel,
        Integer sortOrder,
        Boolean hud
) {

    /**
     * 포인트 행 + 카테고리 스키마 정의로 응답을 만든다.
     *
     * <p><b>{@code sortOrder}/{@code hud} 는 포인트 행에 없다.</b> 두 값은 "이 장비 종류의 이 태그를
     * 몇 번째로, HUD 에 띄울지"라 카테고리의 {@code schema_definition} 에만 있고,
     * 여기서 {@code tagName} 으로 찾아 붙인다. 포인트 컬럼으로 내려쓰지 않는 이유는
     * 정본이 두 곳으로 갈라지면 스키마를 고쳐도 기존 포인트가 따라오지 않기 때문이다.</p>
     *
     * @param schema 같은 tagName 의 스키마 정의. 없으면 null(스키마에서 지워진 태그)
     */
    public static DevicePointResponse from(DevicePoint point, PointSchemaDefinition schema) {
        PointDataSource source = point.getDataSource();
        return new DevicePointResponse(
                point.getPointId(),
                point.getPointKey(),
                point.getTagName(),
                point.getDescription(),
                point.getPointDisplayName(),
                point.getPointType(),
                point.getUnit(),
                point.getActive(),
                point.getRefDeviceCode(),
                point.getRefPointCode(),
                source != null ? source.name() : null,
                source != null ? source.getLabel() : null,
                schema != null ? schema.sortOrder() : null,
                schema != null && schema.hudEnabled()
        );
    }

    /** 스키마를 붙이지 않는 호출용(매핑 등 순서·HUD 가 의미 없는 곳). */
    public static DevicePointResponse from(DevicePoint point) {
        return from(point, null);
    }
}
