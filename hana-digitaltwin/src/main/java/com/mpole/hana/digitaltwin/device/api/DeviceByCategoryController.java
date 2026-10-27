package com.mpole.hana.digitaltwin.device.api;

import com.mpole.hana.digitaltwin.device.dto.DeviceCategoryRequest;
import com.mpole.hana.digitaltwin.device.dto.DeviceCategoryResponse;
import com.mpole.hana.digitaltwin.device.dto.DeviceResponse;
import com.mpole.hana.digitaltwin.device.dto.DeviceResponseWithDiagram;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import com.mpole.hana.digitaltwin.device.service.DeviceCategoryService;
import com.mpole.hana.digitaltwin.device.service.DeviceService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.realtime.dto.CurrentValueSchemaSyncResponse;
import com.mpole.hana.digitaltwin.realtime.dto.PointSchemaSyncResponse;
import com.mpole.hana.digitaltwin.realtime.service.CurrentValueSchemaSyncService;
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
    private final CurrentValueSchemaSyncService currentValueSchemaSyncService;

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
            description = "특정 카테고리 ID(categoryId)를 기준으로 하위 트리 정보를 조회합니다.",
            parameters ={
                @Parameter(name = "categoryId", description = "장비 카테고리 ID", example = "1", required = true),
            }
    )
    //endregion
    @GetMapping("/device-categories/{categoryId}")
    public ResponseEntity<ApiResponse<List<DeviceCategoryResponse>>> getCategoryInfosById(
            @PathVariable Long categoryId) {
        List<DeviceCategoryResponse> response = deviceCategoryService.getCategoryInfoTree(categoryId);
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

    @Operation(
            summary = "현재값 스키마 동기화 미리보기",
            description = "tbl_device_current_value.points 에서 카테고리 스키마에 없는 태그를 찾아 돌려줍니다. 아무것도 바꾸지 않습니다."
    )
    @GetMapping("/device-categories/current-value/schema-sync")
    public ResponseEntity<ApiResponse<CurrentValueSchemaSyncResponse>> previewCurrentValueSchemaSync() {
        return ResponseEntity.ok(ApiResponse.success("현재값 스키마 동기화 미리보기", currentValueSchemaSyncService.preview()));
    }

    @Operation(
            summary = "현재값 스키마 동기화 실행",
            description = "tbl_device_current_value.points 에서 카테고리 스키마에 없는 태그를 지웁니다. "
                    + "스키마가 비어 있는 카테고리의 장비는 건드리지 않습니다."
    )
    @PostMapping("/device-categories/current-value/schema-sync")
    public ResponseEntity<ApiResponse<CurrentValueSchemaSyncResponse>> syncCurrentValueSchema() {
        return ResponseEntity.ok(ApiResponse.success("현재값 스키마 동기화", currentValueSchemaSyncService.sync()));
    }

    @Operation(
            summary = "등록 포인트 스키마 동기화 미리보기",
            description = "tbl_device_point 에서 장비 카테고리 스키마에 없는 태그의 포인트를 찾아 돌려줍니다. 아무것도 바꾸지 않습니다. "
                    + "제어 포인트로 쓰는 포인트는 deletable=false(실행해도 지우지 않음)."
    )
    @GetMapping("/device-categories/point/schema-sync")
    public ResponseEntity<ApiResponse<PointSchemaSyncResponse>> previewPointSchemaSync() {
        return ResponseEntity.ok(ApiResponse.success("등록 포인트 스키마 동기화 미리보기", currentValueSchemaSyncService.previewPoints()));
    }

    @Operation(
            summary = "등록 포인트 스키마 동기화 실행",
            description = """
                    스키마에 없는 등록 포인트를 지우고, 같은 트랜잭션에서 현재값(points)도 정리합니다.
                    · 스키마가 비어 있는 카테고리의 장비는 건드리지 않습니다
                    · 제어 포인트로 쓰는 포인트는 건너뜁니다(skippedPoints)
                    · 이벤트 이력·에너지 스냅샷은 point_id 만 들고 있어 그대로 남습니다
                    · gateway 가 등록 포인트를 다시 읽기 전(기본 5분)에 들어온 값으로 현재값에 태그가 다시 생기면 현재값 동기화를 한 번 더 실행하세요
                    """
    )
    @PostMapping("/device-categories/point/schema-sync")
    public ResponseEntity<ApiResponse<PointSchemaSyncResponse>> syncPointSchema() {
        return ResponseEntity.ok(ApiResponse.success("등록 포인트 스키마 동기화", currentValueSchemaSyncService.syncPoints()));
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
