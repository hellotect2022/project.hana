package com.mpole.hana.digitaltwin.energy.api;

import com.mpole.hana.digitaltwin.energy.dto.EnergySummary;
import com.mpole.hana.digitaltwin.energy.service.EnergySummaryService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 전력량 API (2026-10-12) — 전력감시·원격검침의 금일·금월 전력량.
 *
 * <p>조회지만 POST 다 — 장비가 수백 개면 GET 쿼리스트링(deviceIds=…)이 Tomcat 요청줄 한도(기본 8KB)를 넘을 수 있어
 * 장비 목록을 본문으로 받는다(2026-10-12 사용자 결정).</p>
 */
@RestController
@RequestMapping("/api/energy")
@RequiredArgsConstructor
@Tag(name = "Energy", description = "누적 전력량 기반 금일·금월 전력량")
public class EnergyController {

    private final EnergySummaryService energySummaryService;

    /**
     * 조회 조건.
     *
     * @param deviceIds 장비 id 목록. 비우거나 본문을 생략하면 누적 포인트 전체
     */
    public record SummaryRequest(
            @Schema(description = "장비 id 목록. 비우거나 본문을 생략하면 누적 포인트 전체", example = "[24420, 24166]")
            List<Long> deviceIds
    ) {
    }

    @Operation(
            summary = "금일·금월 전력량",
            description = """
                    누적 포인트(카테고리 스키마에서 "누적" 을 켠 태그)별 현재값·금일·금월 전력량.
                    · 요청 본문 `{ "deviceIds": [24420, 24166] }` — 생략하거나 빈 배열이면 누적 포인트 전체
                    · 금일 = 현재값 − 오늘 00:00 기준값, 금월 = 현재값 − 이달 1일 00:00 기준값 (KST, 5분 스냅샷 기준)
                    · 계량기 교체로 음수가 되면 0
                    · 값은 스키마 배율이 이미 적용된 값, 단위는 unit
                    · 기준값 = periodStart 이하 가장 가까운 스냅샷, 없으면 periodStart 이후 가장 가까운 스냅샷(baseAdjusted=true, 그 앞 사용량은 빠짐)
                    · baseAt = 실제로 쓴 기준 스냅샷 시각. 스냅샷이 아예 없으면 value·baseAt 이 null
                    · stale=true 면 현재값이 15분 넘게 갱신되지 않음(통신 끊김 의심)
                    """
    )
    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<List<EnergySummary>>> summary(
            @RequestBody(required = false) SummaryRequest request
    ) {
        List<Long> deviceIds = request == null ? null : request.deviceIds();
        return ResponseEntity.ok(ApiResponse.success("금일·금월 전력량", energySummaryService.summary(deviceIds)));
    }
}
