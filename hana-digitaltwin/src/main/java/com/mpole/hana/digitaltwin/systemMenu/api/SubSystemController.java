package com.mpole.hana.digitaltwin.systemMenu.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.systemMenu.dto.SubSystemCreateRequest;
import com.mpole.hana.digitaltwin.systemMenu.dto.SubSystemResponse;
import com.mpole.hana.digitaltwin.systemMenu.dto.SubSystemSortOrderRequest;
import com.mpole.hana.digitaltwin.systemMenu.dto.SubSystemUpdateRequest;
import com.mpole.hana.digitaltwin.systemMenu.service.SubSystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device-system/sub-systems")
@RequiredArgsConstructor
@Tag(name = "Sub System", description = "하위 시스템(subSystem) 관리 API")
public class SubSystemController {

    private final SubSystemService subSystemService;

    @Operation(summary = "하위 시스템 목록 조회", description = "systemId에 속한 하위 시스템 목록을 정렬 순서대로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SubSystemResponse>>> getSubSystems(
            @RequestParam(name = "systemId") Long systemId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("하위 시스템 목록 조회", subSystemService.getSubSystemsBySystemId(systemId))
        );
    }

    @Operation(summary = "하위 시스템 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createSubSystem(
            @Valid @RequestBody SubSystemCreateRequest request
    ) {
        subSystemService.createSubSystem(request);
        return ResponseEntity.ok(ApiResponse.success("하위 시스템 생성"));
    }

    @Operation(summary = "하위 시스템 수정")
    @PutMapping("/{subSystemId}")
    public ResponseEntity<ApiResponse<Void>> updateSubSystem(
            @PathVariable Long subSystemId,
            @Valid @RequestBody SubSystemUpdateRequest request
    ) {
        subSystemService.updateSubSystem(subSystemId, request);
        return ResponseEntity.ok(ApiResponse.success("하위 시스템 수정"));
    }

    @Operation(summary = "하위 시스템 순서 변경")
    @PutMapping("/sort-orders")
    public ResponseEntity<ApiResponse<Void>> updateSubSystemSortOrders(
            @Valid @RequestBody List<SubSystemSortOrderRequest> requests
    ) {
        subSystemService.updateSubSystemSortOrders(requests);
        return ResponseEntity.ok(ApiResponse.success("하위 시스템 순서 변경 저장"));
    }

    @Operation(summary = "하위 시스템 삭제")
    @DeleteMapping("/{subSystemId}")
    public ResponseEntity<ApiResponse<Void>> deleteSubSystem(
            @PathVariable Long subSystemId
    ) {
        subSystemService.deleteSubSystem(subSystemId);
        return ResponseEntity.ok(ApiResponse.success("하위 시스템 삭제"));
    }
}
