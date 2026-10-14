package com.mpole.hana.digitaltwin.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 알람 이력 검색 조건 — {@code POST /api/event/history}, {@code POST /api/event/history/stats} 공용 본문 (2026-10-14).
 *
 * <p>모든 필드는 생략 가능. 집계(/stats)는 page·size·completed 를 무시한다.</p>
 */
@Schema(description = "알람 이력 검색 조건. 모든 필드 생략 가능")
public record AlarmHistorySearchRequest(
        @Schema(description = "장비 카테고리 id 목록. 대·중분류는 하위 소분류 전체로 확장해 합친다. 생략·빈 목록 = 전체", example = "[21, 22]")
        List<Long> categoryIds,

        @Schema(description = "층 ID(위치 필터)", example = "4")
        Long floorId,

        @Schema(description = "알람명(규칙) ID", example = "7")
        Long ruleId,

        @Schema(description = "특정 장비만")
        Long deviceId,

        @Schema(description = "심각도(CAUTION/WARNING/CRITICAL)")
        String level,

        @Schema(description = "true=완료(확인됨), false=미완료(미확인), 생략=전체. /stats 에서는 무시", example = "false")
        Boolean completed,

        @Schema(description = "true=모의(SOP 훈련)만, false=실제만, 생략=전체", example = "false")
        Boolean simulated,

        @Schema(description = "검색어 — 알람명·장비명·장비키 부분일치(대소문자 무시)", example = "청소")
        String q,

        @Schema(description = "발생시각 시작(ISO-8601). 화면 기본은 최근 7일", example = "2026-10-07T00:00:00+09:00")
        OffsetDateTime from,

        @Schema(description = "발생시각 종료(ISO-8601)", example = "2026-10-14T23:59:59+09:00")
        OffsetDateTime to,

        @Schema(description = "페이지 번호(0부터). 기본 0. /stats 에서는 무시", example = "0")
        Integer page,

        @Schema(description = "페이지 크기. 기본 20. /stats 에서는 무시", example = "20")
        Integer size
) {
}
