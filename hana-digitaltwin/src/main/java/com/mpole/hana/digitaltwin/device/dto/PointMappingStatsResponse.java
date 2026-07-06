package com.mpole.hana.digitaltwin.device.dto;

/**
 * 포인트 매핑 통계.
 * <p>미매핑 토글과 무관하게 category/keyword 필터 범위 기준으로 산정한다.</p>
 *
 * @param total  범위 내 전체 포인트 수
 * @param mapped 범위 내 SI 매핑(ref_device_code + ref_point_code) 완료 포인트 수
 */
public record PointMappingStatsResponse(
        long total,
        long mapped
) {
}
