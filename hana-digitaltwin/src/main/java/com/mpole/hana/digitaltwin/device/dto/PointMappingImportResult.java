package com.mpole.hana.digitaltwin.device.dto;

import java.util.List;

/**
 * 포인트 매핑 엑셀 업로드 결과.
 *
 * @param total   파싱된 데이터 행 수(헤더 제외)
 * @param applied 실제 반영된 유효 행 수
 * @param failed  반영되지 못한 행 목록(사유 포함)
 */
public record PointMappingImportResult(
        int total,
        int applied,
        List<FailedRow> failed
) {
    /**
     * 반영 실패한 엑셀 행 정보.
     *
     * @param row      엑셀 행 번호(1-based, 사용자 표시용)
     * @param pointKey 해당 행의 point_key(없으면 빈 문자열)
     * @param reason   실패 사유
     */
    public record FailedRow(
            int row,
            String pointKey,
            String reason
    ) {
    }
}
