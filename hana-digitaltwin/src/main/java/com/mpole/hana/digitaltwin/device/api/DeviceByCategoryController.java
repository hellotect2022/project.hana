package com.mpole.hana.digitaltwin.device.api;

import com.mpole.hana.digitaltwin.device.dto.DeviceCategoryRequest;
import com.mpole.hana.digitaltwin.device.dto.DeviceCategoryResponse;
import com.mpole.hana.digitaltwin.device.dto.DeviceResponse;
import com.mpole.hana.digitaltwin.device.dto.DeviceResponseWithDiagram;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import com.mpole.hana.digitaltwin.device.service.DeviceCategoryService;
import com.mpole.hana.digitaltwin.device.service.DeviceService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Tag(name = "Device Category", description = "장비 카테고리 관리 API")
public class DeviceByCategoryController {
    private final DeviceService deviceService;
    private final DeviceCategoryService deviceCategoryService;

    //region swagger 🔄 장비 카테고리 트리 목록 조회 명세서
    @Operation(
            summary = "장비 카테고리 목록 조회",
            description = "전체 장비 카테고리 정보를 트리(Tree) 구조로 조회합니다."
    )
    //endregion
    @GetMapping("/device-categories")
    public ResponseEntity<ApiResponse<List<DeviceCategoryResponse>>> getCategoryInfos() {
        List<DeviceCategoryResponse> response = deviceCategoryService.getCategoryInfoTree();
        return
                ResponseEntity.ok(ApiResponse.success("장비 카테고리 목록 조회", response));
    }

    //region swagger 🔄 개별 카테고리 정보 조회 명세서
    @Operation(
            summary = "개별 카테고리 정보 조회",
            description = "특정 카테고리 ID(categoryId)를 기준으로 하위 트리 정보를 조회합니다."
    )
    //endregion
    @GetMapping("/device-categories/{categoryId}")
    public ResponseEntity<ApiResponse<List<DeviceCategoryResponse>>> getCategoryInfosById() {
        List<DeviceCategoryResponse> response = deviceCategoryService.getCategoryInfoTree();
        return ResponseEntity.ok(ApiResponse.success("개별 카테고리 정보 조회", response));
    }

    @PostMapping("/device-categories")
    public ResponseEntity<ApiResponse<DeviceCategoryResponse>> createCategory(
            @Valid @RequestBody DeviceCategoryRequest request) {
        DeviceCategoryResponse response = deviceCategoryService.createCategory(request);
        return ResponseEntity.ok(ApiResponse.success("장비 카테고리 생성", response));
    }

    @PutMapping("/device-categories/{categoryId}")
    public ResponseEntity<ApiResponse<DeviceCategoryResponse>> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody DeviceCategoryRequest request) {
        DeviceCategoryResponse response = deviceCategoryService.updateCategory(categoryId, request);
        return ResponseEntity.ok(ApiResponse.success("장비 카테고리 수정", response));
    }

    @DeleteMapping("/device-categories/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long categoryId) {
        deviceCategoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success("장비 카테고리 삭제"));
    }

    @PutMapping("/device-categories/{categoryId}/schema")
    public ResponseEntity<ApiResponse<DeviceCategoryResponse>> updateCategorySchema(
            @PathVariable Long categoryId,
            @RequestBody List<PointSchemaDefinition> schema) {
        DeviceCategoryResponse response = deviceCategoryService.updateSchema(categoryId, schema);
        return ResponseEntity.ok(ApiResponse.success("스키마 정의 업데이트", response));
    }

    //region swagger 🔄 카테고리별 장비 목록 조회 명세서
    @Operation(
            summary = "카테고리별 장비 목록 조회",
            description = "특정 카테고리 ID(categoryId)에 속한 전체 장비 목록을 조회합니다.",
            parameters = {
                    @Parameter(name = "categoryId", description = "장비 카테고리 ID", example = "1", required = true),
                    @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", example = "0"),
                    @Parameter(name = "size", description = "페이지 당 데이터 개수", example = "20")
            }
    )
    //endregion
    @GetMapping("/device-categories/{categoryId}/devices")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<DeviceResponse>>> getDeviceInCategories(
            @PathVariable Long categoryId,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        Page<DeviceResponse> response = deviceService.getTargetCategoryDevices(categoryId,pageable);
        return ResponseEntity.ok(ApiResponse.success("카테고리 별 장비 목록 조회", response));
    }

    //region swagger 🔄 카테고리별 장비 목록 조회 명세서
    @Operation(
            summary = "카테고리별 장비 목록 조회 (V2)",
            description = "특정 카테고리 ID(categoryId)에 속한 전체 장비 목록을 조회합니다. 계통도 포함",
            parameters = {
                    @Parameter(name = "categoryId", description = "장비 카테고리 ID", example = "1", required = true),
                    @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", example = "0"),
                    @Parameter(name = "size", description = "페이지 당 데이터 개수", example = "20")
            }
    )
    //endregion
    @GetMapping("/v2/device-categories/{categoryId}/devices")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<DeviceResponseWithDiagram>>> getDeviceInCategoriesWithSystemDiagram(
            @PathVariable Long categoryId,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable) {
        Page<DeviceResponseWithDiagram> response = deviceService.getTargetCategoryDevicesWithDiagram(categoryId, pageable);
        return ResponseEntity.ok(ApiResponse.success("카테고리 별 장비 목록 조회(계통도 포함)", response));
    }

    //region swagger 🔄 카테고리별 장비 개수 조회 명세서
    @Operation(
            summary = "카테고리별 장비 개수 조회",
            description = "특정 카테고리 ID(categoryId)에 속한 장비의 총 개수를 조회합니다."
    )
    //endregion
    @GetMapping("/device-categories/{categoryId}/devices-cnt")
    public ResponseEntity<ApiResponse<Long>> getDeviceInCategoriesCnt(@PathVariable Long categoryId) {
        long cnt = deviceService.getTargetCategoryDevicesCnt(categoryId);
        return ResponseEntity.ok(ApiResponse.success("카테고리 별 장비 갯수", cnt));
    }
}
