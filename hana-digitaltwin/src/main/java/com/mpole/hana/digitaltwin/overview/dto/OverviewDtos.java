package com.mpole.hana.digitaltwin.overview.dto;

import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.MemberType;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.Period;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.Section;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.ValueSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 종합 현황(overview) API DTO 모음 (2026-10-16). */
public final class OverviewDtos {

    private OverviewDtos() {
    }

    /* ───────────────────────── 관리자 설정 ───────────────────────── */

    @Schema(description = "항목 등록/수정. 수정 시 구성 대상은 통째로 교체")
    public record ItemRequest(
            @Schema(description = "영역", example = "ENERGY_USAGE") Section section,
            @Schema(description = "표시 이름", example = "전력") String label,
            @Schema(description = "값 출처. INFRA_STATUS 는 생략", example = "SNAPSHOT_DELTA") ValueSource valueSource,
            @Schema(description = "SNAPSHOT_DELTA 기간(TODAY/MONTH/YEAR). CURRENT_VALUE 면 생략", example = "TODAY") Period period,
            @Schema(description = "표시 단위", example = "MWh") String unit,
            @Schema(description = "표시 배율(값 x scale), 0보다 큼. 생략 시 1", example = "0.001") BigDecimal scale,
            @Schema(description = "소수 자릿수 0~6. 생략 시 1", example = "1") Integer decimals,
            @Schema(description = "클라이언트 표시 여부. 생략 시 true") Boolean active,
            @Schema(description = "구성 대상(순서대로). 에너지 영역은 POINT/CATEGORY 1개 이상, INFRA_STATUS 는 INFRA 1개") List<MemberRequest> members
    ) {
    }

    @Schema(description = "구성 대상 1건")
    public record MemberRequest(
            @Schema(description = "POINT / CATEGORY / INFRA", example = "POINT") MemberType memberType,
            @Schema(description = "POINT — tbl_device_point.point_id") Long pointId,
            @Schema(description = "CATEGORY — 소분류 category_id") Long categoryId,
            @Schema(description = "CATEGORY — 합산할 태그명", example = "total-g") String tagName,
            @Schema(description = "INFRA — tbl_device_infra.infra_id") Long infraId
    ) {
    }

    @Schema(description = "영역 안 순서 변경 — itemIds 순서대로 1, 2, 3 …")
    public record OrderRequest(Section section, List<Long> itemIds) {
    }

    /** 관리자 목록 1건 */
    public record ItemResponse(
            Long itemId,
            Section section,
            String label,
            ValueSource valueSource,
            Period period,
            String unit,
            BigDecimal scale,
            Integer decimals,
            Integer sortOrder,
            boolean active,
            List<MemberResponse> members,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    /**
     * 구성 대상 1건 + 화면 표시 정보.
     *
     * @param displayName POINT: 포인트키 / CATEGORY: 분류 경로(대 › 중 › 소) / INFRA: 인프라 이름
     * @param subLabel    POINT: 장비명 / CATEGORY: 태그명 / INFRA: ref 코드
     * @param accumulate  카테고리 스키마 누적 옵션(POINT·CATEGORY). INFRA 는 null
     * @param pointCount  CATEGORY: 지금 합산 대상 포인트 수
     * @param missing     대상이 지워져 찾을 수 없음
     */
    public record MemberResponse(
            Long memberId,
            MemberType memberType,
            Long pointId,
            Long categoryId,
            String tagName,
            Long infraId,
            String displayName,
            String subLabel,
            Boolean accumulate,
            Integer pointCount,
            boolean missing
    ) {
    }

    /* ───────────────────────── 클라이언트 조회 ───────────────────────── */

    @Schema(description = "종합 현황 — 영역별 사용 중인 항목(순서대로)")
    public record OverviewResponse(
            OffsetDateTime asOf,
            List<ValueCard> energyUsage,
            List<ValueCard> energyProduction,
            List<InfraCard> infraStatus
    ) {
    }

    /**
     * 에너지 카드 1칸.
     *
     * @param value         표시 값(배율·자릿수 적용). 계산할 포인트가 하나도 없으면 null
     * @param pointCount    합산 대상 포인트 수(분류는 장비 전체로 펼친 수)
     * @param excludedCount 계산에서 빠진 포인트 수(현재값 없음·숫자 아님·기준 스냅샷 없음)
     * @param staleCount    현재값이 15분 넘게 갱신되지 않은 포인트 수(통신 끊김 의심)
     * @param baseAt        SNAPSHOT_DELTA 기준 스냅샷 시각(포인트 중 가장 이른 것)
     * @param baseAdjusted  기간 시작 이전 스냅샷이 없어 기간 안 첫 스냅샷을 쓴 포인트가 있음
     */
    public record ValueCard(
            Long itemId,
            String label,
            BigDecimal value,
            String unit,
            Integer decimals,
            ValueSource valueSource,
            Period period,
            OffsetDateTime periodStart,
            int pointCount,
            int excludedCount,
            int staleCount,
            OffsetDateTime baseAt,
            boolean baseAdjusted
    ) {
    }

    /** 통신 상태 칩 1칸 — tbl_device_infra 그대로 */
    public record InfraCard(
            Long itemId,
            String label,
            Long infraId,
            String infraName,
            String state,
            OffsetDateTime lastCommAt,
            boolean missing
    ) {
    }
}
