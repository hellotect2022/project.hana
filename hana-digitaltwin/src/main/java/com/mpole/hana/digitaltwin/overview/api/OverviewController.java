package com.mpole.hana.digitaltwin.overview.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.ItemRequest;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.ItemResponse;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.OrderRequest;
import com.mpole.hana.digitaltwin.overview.dto.OverviewDtos.OverviewResponse;
import com.mpole.hana.digitaltwin.overview.entity.OverviewEnums.Section;
import com.mpole.hana.digitaltwin.overview.service.OverviewItemService;
import com.mpole.hana.digitaltwin.overview.service.OverviewQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 종합 현황(overview) API (2026-10-16).
 * 클라이언트 메인 "종합 현황" 패널 값 조회 + 관리자 항목 설정.
 */
@RestController
@RequestMapping("/api/overview")
@RequiredArgsConstructor
@Tag(name = "Overview", description = "종합 현황 — 클라이언트 패널 값 조회 + 관리자 항목 설정")
public class OverviewController {

    private final OverviewQueryService queryService;
    private final OverviewItemService itemService;

    @Operation(
            summary = "종합 현황 조회 (클라이언트)",
            description = """
                    사용 중인 항목을 영역별·순서대로 계산해 돌려준다.
                    · energyUsage / energyProduction: 값 출처별 계산
                      - CURRENT_VALUE  = Σ 현재값(tbl_device_current_value)
                      - SNAPSHOT_DELTA = Σ max(현재값 − 기간 시작 스냅샷, 0) (tbl_energy_snapshot). 기간 TODAY/MONTH/YEAR, KST
                      - value = 합 × scale, decimals 자리 반올림. 계산할 포인트가 없으면 null
                      - excludedCount = 현재값 없음·숫자 아님·스냅샷 없음으로 빠진 포인트 수, staleCount = 15분 넘게 갱신 안 된 포인트 수
                      - baseAdjusted = 기간 시작 이전 스냅샷이 없어 기간 안 첫 스냅샷을 쓴 포인트가 있음
                    · infraStatus: tbl_device_infra.network_state(NORMAL/ABNORMAL/DISCONNECTED/UNKNOWN) 그대로
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<OverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.success("종합 현황", queryService.overview()));
    }

    @Operation(summary = "항목 목록 (관리자)",
            description = "구성 대상에 표시 정보(포인트키·장비명·분류 경로·누적 여부·삭제 여부)를 붙여 준다. section 생략 시 전체",
            parameters = @Parameter(name = "section", description = "ENERGY_USAGE / ENERGY_PRODUCTION / INFRA_STATUS"))
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> items(@RequestParam(required = false) Section section) {
        return ResponseEntity.ok(ApiResponse.success("종합 현황 항목 목록", itemService.list(section)));
    }

    @Operation(summary = "항목 등록 (관리자)",
            description = """
                    영역 맨 뒤에 추가한다.
                    · INFRA_STATUS: valueSource·period 없이 INFRA 1개
                    · 에너지 영역: valueSource 필수, POINT/CATEGORY 1개 이상
                    · SNAPSHOT_DELTA: period 필수, 모든 대상이 누적 옵션이어야 저장된다(아니면 400)
                    """)
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<ItemResponse>> create(@RequestBody ItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("종합 현황 항목 등록", itemService.create(request)));
    }

    @Operation(summary = "항목 수정 (관리자)", description = "구성 대상은 통째로 교체한다. 영역은 바꿀 수 없다. 저장 규칙은 등록과 같다.")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<ItemResponse>> update(@PathVariable Long itemId, @RequestBody ItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("종합 현황 항목 수정", itemService.update(itemId, request)));
    }

    @Operation(summary = "항목 삭제 (관리자)", description = "구성 대상도 함께 삭제된다.")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long itemId) {
        itemService.delete(itemId);
        return ResponseEntity.ok(ApiResponse.success("종합 현황 항목 삭제"));
    }

    @Operation(summary = "영역 안 순서 변경 (관리자)", description = "itemIds 순서대로 1, 2, 3 … 바뀐 영역의 목록을 돌려준다.")
    @PutMapping("/items/order")
    public ResponseEntity<ApiResponse<List<ItemResponse>>> reorder(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("종합 현황 순서 변경", itemService.reorder(request)));
    }

    @Operation(summary = "항목 미리보기 (관리자)", description = "저장된 항목 1개의 지금 계산 값. 사용 여부와 무관하게 계산한다.")
    @GetMapping("/items/{itemId}/preview")
    public ResponseEntity<ApiResponse<OverviewResponse>> preview(@PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success("종합 현황 항목 미리보기", queryService.preview(itemId)));
    }
}
