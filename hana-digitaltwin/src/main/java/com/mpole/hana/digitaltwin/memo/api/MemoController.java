package com.mpole.hana.digitaltwin.memo.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.memo.dto.MemoRequest;
import com.mpole.hana.digitaltwin.memo.dto.MemoResponse;
import com.mpole.hana.digitaltwin.memo.service.MemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 운영 메모(임시 기록용 메모장).
 *
 * <p>업무 도메인과 무관한 부록 기능이다.</p>
 */
@Tag(name = "메모", description = "운영 메모(임시 기록용)")
@RestController
@RequestMapping("/api/memo")
@RequiredArgsConstructor
public class MemoController {

    private final MemoService memoService;

    //region swagger 🔄 메모 목록 명세서
    @Operation(
            summary = "메모 목록 조회",
            description = "최근 수정순으로 정렬해 반환합니다. keyword 는 제목·본문 부분일치입니다.",
            parameters = {
                    @Parameter(name = "page", description = "0-based 페이지 번호", example = "0"),
                    @Parameter(name = "size", description = "페이지 크기", example = "20"),
                    @Parameter(name = "keyword", description = "제목·본문 검색어", example = "점검")
            }
    )
    //endregion
    @GetMapping
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<MemoResponse>>> getMemos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword
    ) {
        Page<MemoResponse> result = memoService.getMemos(
                keyword,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        return ResponseEntity.ok(ApiResponse.success("메모 목록 조회", result));
    }

    //region swagger 🔄 메모 단건 명세서
    @Operation(summary = "메모 단건 조회")
    //endregion
    @GetMapping("/{memoId}")
    public ResponseEntity<ApiResponse<MemoResponse>> getMemo(@PathVariable Long memoId) {
        return ResponseEntity.ok(ApiResponse.success("메모 조회", memoService.getMemo(memoId)));
    }

    //region swagger 🔄 메모 등록 명세서
    @Operation(summary = "메모 등록", description = "제목은 필수, 본문은 선택입니다.")
    //endregion
    @PostMapping
    public ResponseEntity<ApiResponse<MemoResponse>> createMemo(@Valid @RequestBody MemoRequest request) {
        return ResponseEntity.ok(ApiResponse.success("메모가 등록되었습니다.", memoService.createMemo(request)));
    }

    //region swagger 🔄 메모 수정 명세서
    @Operation(summary = "메모 수정")
    //endregion
    @PutMapping("/{memoId}")
    public ResponseEntity<ApiResponse<MemoResponse>> updateMemo(
            @PathVariable Long memoId,
            @Valid @RequestBody MemoRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("메모가 수정되었습니다.", memoService.updateMemo(memoId, request)));
    }

    //region swagger 🔄 메모 삭제 명세서
    @Operation(summary = "메모 삭제")
    //endregion
    @DeleteMapping("/{memoId}")
    public ResponseEntity<ApiResponse<Void>> deleteMemo(@PathVariable Long memoId) {
        memoService.deleteMemo(memoId);
        return ResponseEntity.ok(ApiResponse.success("메모가 삭제되었습니다."));
    }
}
