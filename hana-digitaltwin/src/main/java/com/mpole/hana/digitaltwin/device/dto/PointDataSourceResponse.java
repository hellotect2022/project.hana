package com.mpole.hana.digitaltwin.device.dto;

import com.mpole.hana.common.constant.PointDataSource;

/**
 * 포인트 원천 소스 선택지 — 매핑 화면 드롭다운용.
 *
 * @param code  저장되는 코드값 (예: {@code AMQP})
 * @param label 화면 표시용 한글 라벨
 */
public record PointDataSourceResponse(
        String code,
        String label
) {
    public static PointDataSourceResponse from(PointDataSource source) {
        return new PointDataSourceResponse(source.name(), source.getLabel());
    }
}
