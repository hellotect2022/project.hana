package com.mpole.hana.digitaltwin.device.api;

import com.mpole.hana.digitaltwin.device.dto.*;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import com.mpole.hana.digitaltwin.device.service.DeviceCategoryService;
import com.mpole.hana.digitaltwin.device.service.DevicePlacementService;
import com.mpole.hana.digitaltwin.device.service.DeviceService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityLocationResponse;
import com.mpole.hana.digitaltwin.location.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceController {
//    private final MssqlRepository mssqlRepository;
//    private final XnSolutionService xnSolutionService;

    private final DeviceService deviceService;
    private final DeviceCategoryService deviceCategoryService;
    private final DevicePlacementService devicePlacementService;
    private final LocationService locationService;
    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> saveDevices(@RequestBody DeviceUpdateRequest request) {
        log.info("장비 저장: deviceKey={}, points={}",
                request.deviceKey(),
                request.points() != null ? request.points().size() : 0);
        DeviceResponse created = deviceService.saveDevice(request);
        int pointCount = request.points() != null ? request.points().size() : 0;
        String message = pointCount > 0
                ? "장비 및 포인트 " + pointCount + "건 저장 성공"
                : "장비 저장 성공";
        return ResponseEntity.ok(ApiResponse.success(message, created));
    }

//    @GetMapping
//    public ResponseEntity<List<ExternalMSViewEntity>> getViewTableDevices() {
//        log.info("view 테이블 장비 조회");
//        List<ExternalMSViewEntity> list = mssqlRepository.findAll();
//        return ResponseEntity.ok(list);
//    }
//
//    @GetMapping("/sync")
//    public ResponseEntity<String> syncDevice() {
//        log.info("장비 동기화 시작");
//        xnSolutionService.syncInBatches();
//        return ResponseEntity.ok("동기화가 완료되었습니다.");
//    }

    @GetMapping("/cnt-all")
    public ResponseEntity<ApiResponse<Long>> getAllDevicesCnt() {
        Long cnt = deviceService.getAllDevicesCnt();
        return ResponseEntity.ok(ApiResponse.success("장비 갯수 조회", cnt));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getAllDevices() {
        List<DeviceResponse> devices = deviceService.getAllDevices();
        return ResponseEntity.ok(ApiResponse.success("장비 목록 조회", devices));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getTargetDevice(@PathVariable Long deviceId) {
        DeviceResponse device = deviceService.getTargetDevice(deviceId);
        return ResponseEntity.ok(ApiResponse.success("장비 정보 조회", device));
    }

    @PatchMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<DeviceResponse>> updateDevice(
            @PathVariable Long deviceId,
            @RequestBody DeviceUpdateRequest request) {
        DeviceResponse updated = deviceService.updateDevice(deviceId, request);
        return ResponseEntity.ok(ApiResponse.success("장비 정보 수정", updated));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getSearchDevices(
            @RequestParam(required = false) Long floor,
            @RequestParam(required = false) Long zone) {
        List<DeviceResponse> devices = deviceService.getSearchDevices(floor, zone);
        return ResponseEntity.ok(ApiResponse.success("층, 구역 별 장비 목록 조회", devices));
    }

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

    @PutMapping("/placement")
    public ResponseEntity<ApiResponse<DevicePlacementResponse>> saveDevicesPlacement(
            @Valid @RequestBody DevicePlacementRequest request) {
        DevicePlacementResponse response = devicePlacementService.save(request);
        return ResponseEntity.ok(ApiResponse.success("장비 배치정보 업데이트", response));
    }

    @DeleteMapping("/placement")
    public ResponseEntity<ApiResponse<Void>> deleteDevicesPlacement(
            @RequestBody Map<String, List<Long>> request) {
        List<Long> deviceIds = request.get("deviceIds");
        devicePlacementService.deleteByDeviceIds(deviceIds);
        return ResponseEntity.ok(ApiResponse.success("장비 배치정보 삭제"));
    }

//    @GetMapping("/xn/points")
//    public ResponseEntity<ApiResponse<Page<XnViewEntity>>> getXnDevicePoints(
//            @PageableDefault(size = 10) Pageable pageable) {
//        Page<XnViewEntity> points = xnSolutionService.getXnDevicePoints(pageable);
//        return ResponseEntity.ok(ApiResponse.success("장비 포인트 목록 조회", points));
//    }

//    @PostMapping("/xn/points")
//    public ResponseEntity<ApiResponse<Page<XnViewEntity>>> getDevicePoints(
//            @RequestBody XnPointSearchRequest request,
//            @PageableDefault(size = 10) Pageable pageable) {
//        Page<XnViewEntity> result = xnSolutionService.getXnDevicePoints(pageable);
//        return ResponseEntity.ok(ApiResponse.success("장비 포인트 목록 조회", result));
//    }

    @PutMapping("/{deviceId}/points/tag-mapping")
    public ResponseEntity<ApiResponse<Void>> saveTagMapping(
            @PathVariable Long deviceId,
            @RequestBody Map<String, String> mappings) {
        return ResponseEntity.ok(ApiResponse.success("tagName 매핑 저장"));
    }

    @PostMapping("/{deviceId}/cctv-mapping")
    public ResponseEntity<ApiResponse<Void>> postCctvMapping(
            @PathVariable Long deviceId,
            @RequestBody Map<String, List<Long>> request) {

        List<Long> cctvIds = request.get("cctvIds");
        deviceService.postCctvDeviceMapping(deviceId, cctvIds);
        return ResponseEntity.ok(ApiResponse.success("장비 주변 CCTV 매핑 완료"));
    }

    @GetMapping("/{deviceId}/cctv-mapping")
    public ResponseEntity<ApiResponse<List<CctvInfoProjection>>> getCctvMapping(
            @PathVariable Long deviceId) {
        List<CctvInfoProjection> list = deviceService.getCctvDeviceMapping(deviceId);
        return ResponseEntity.ok(ApiResponse.success("장비 주변 CCTV 매핑 조회", list));
    }
}
