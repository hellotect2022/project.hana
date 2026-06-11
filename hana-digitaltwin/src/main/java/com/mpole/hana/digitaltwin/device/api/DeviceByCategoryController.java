package com.mpole.hana.digitaltwin.device.api;

import com.mpole.hana.digitaltwin.device.dto.DeviceCategoryRequest;
import com.mpole.hana.digitaltwin.device.dto.DeviceCategoryResponse;
import com.mpole.hana.digitaltwin.device.dto.DeviceResponse;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import com.mpole.hana.digitaltwin.device.service.DeviceCategoryService;
import com.mpole.hana.digitaltwin.device.service.DeviceService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceByCategoryController {
    private final DeviceService deviceService;
    private final DeviceCategoryService deviceCategoryService;

    @GetMapping("/device-categories")
    public ResponseEntity<ApiResponse<List<DeviceCategoryResponse>>> getCategoryInfos() {
        List<DeviceCategoryResponse> response = deviceCategoryService.getCategoryInfoTree();
        return
                ResponseEntity.ok(ApiResponse.success("장비 카테고리 목록 조회", response));
    }

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

    @GetMapping("/device-categories/{categoryId}/devices")
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getDeviceInCategories(
            @PathVariable Long categoryId) {
        List<DeviceResponse> response = deviceService.getTargetCategoryDevices(categoryId);
        return ResponseEntity.ok(ApiResponse.success("카테고리 별 장비 목록 조회", response));
    }

    @GetMapping("/device-categories/{categoryId}/devices-cnt")
    public ResponseEntity<ApiResponse<Long>> getDeviceInCategoriesCnt(@PathVariable Long categoryId) {
        long cnt = deviceService.getTargetCategoryDevicesCnt(categoryId);
        return ResponseEntity.ok(ApiResponse.success("카테고리 별 장비 갯수", cnt));
    }
}
