package com.mpole.hana.digitaltwin.device.api;

import com.mpole.hana.digitaltwin.device.dto.*;
import com.mpole.hana.digitaltwin.device.schema.PointSchemaDefinition;
import com.mpole.hana.digitaltwin.device.service.DeviceCategoryService;
import com.mpole.hana.digitaltwin.device.service.DevicePlacementService;
import com.mpole.hana.digitaltwin.device.service.DevicePointMappingExcelService;
import com.mpole.hana.digitaltwin.device.service.DeviceService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityLocationResponse;
import com.mpole.hana.digitaltwin.location.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Validated
//여기서 리스트 "안쪽 원소" 검증(List<@Valid ...>)과 @NotEmpty 같은 파라미터 레벨 제약은 그냥 두면 동작하지 않습니다. 이걸 켜주는 스위치가 클래스 상단의 @Validated입니다.
//@Validated를 클래스에 붙이면 → 스프링이 그 빈을 MethodValidationPostProcessor로 프록시로 감싸서, 메서드 호출 시점에 파라미터/리스트 원소 제약까지 검사합니다
@Tag(name = "Device", description = "기본 장비 관리 API")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceCategoryService deviceCategoryService;
    private final DevicePlacementService devicePlacementService;
    private final DevicePointMappingExcelService devicePointMappingExcelService;
    private final LocationService locationService;

    //region swagger 🔄 장비 및 포인트 저장 명세서
    @Operation(
            summary = "장비 및 포인트 저장",
            description = "새로운 장비 정보와 해당 장비에 연결된 데이터 수집 포인트(Point) 목록을 함께 저장하거나 수정합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "저장 및 수정할 장비와 포인트 데이터 정보",
                    required = true,
                    content = @Content(schema = @Schema(implementation = DeviceUpdateRequest.class))
            )
    )
    //endregion
    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> saveDevices(@RequestBody DeviceUpdateRequest request) {
        log.info("장비 저장: deviceKey={}, points={}", request.deviceKey(), request.points() != null ? request.points().size() : 0);
        DeviceResponse created = deviceService.saveDevice(request);
        int pointCount = request.points() != null ? request.points().size() : 0;
        String message = pointCount > 0
                ? "장비 및 포인트 " + pointCount + "건 저장 성공"
                : "장비 저장 성공";
        return ResponseEntity.ok(ApiResponse.success(message, created));
    }

    //region swagger 🔄 전체 장비 개수 조회 명세서
    @Operation(
            summary = "전체 장비 개수 조회",
            description = "시스템에 등록된 모든 장비의 총 개수를 조회합니다."
    )
    //endregion
    @GetMapping("/cnt-all")
    public ResponseEntity<ApiResponse<Long>> getAllDevicesCnt() {
        Long cnt = deviceService.getAllDevicesCnt();
        return ResponseEntity.ok(ApiResponse.success("장비 갯수 조회", cnt));
    }

    //region swagger 🔄 장비 목록 페이징 조회 명세서
    @Operation(
            summary = "장비 목록 페이징 조회",
            description = "검색 키워드 및 카테고리 필터를 적용하여 장비 목록을 페이징 형태로 조회합니다.",
            parameters = {
                    @Parameter(name = "keyword", description = "검색 키워드 (장비명 등)", example = "", required = false),
                    @Parameter(name = "categoryId", description = "카테고리 ID 필터", example = "", required = false),
                    @Parameter(name = "page", hidden = true, description = "페이지 번호 (0부터 시작)", example = "0"),
                    @Parameter(name = "size", hidden = true, description = "페이지 당 데이터 개수", example = "3")
            }
    )
    //endregion
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<DeviceResponse>>> getAllDevices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @Parameter(hidden = true) @PageableDefault(size = 20, sort = "deviceId", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<DeviceResponse> devices = deviceService.getDevicesPage(keyword, categoryId, pageable);

        return ResponseEntity.ok(ApiResponse.success("장비 목록 조회", devices));
    }

    //region swagger 🔄 장비 상세 정보 조회 명세서
    @Operation(
            summary = "장비 상세 정보 조회",
            description = "특정 장비 ID(deviceId)를 기준으로 해당 장비의 상세 정보를 조회합니다.",
            parameters = {
                    @Parameter(name = "deviceId", description = "조회할 장비 ID", example = "100", required = true)
            }
    )
    //endregion
    @GetMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<DeviceResponse>> getTargetDevice(@PathVariable Long deviceId) {
        DeviceResponse device = deviceService.getTargetDevice(deviceId);
        return ResponseEntity.ok(ApiResponse.success("장비 정보 조회", device));
    }

    @GetMapping("/{deviceId}/points")
    public ResponseEntity<ApiResponse<List<DevicePointResponse>>> getDevicePoints(
            @PathVariable Long deviceId
    ) {
        List<DevicePointResponse> points = deviceService.getDevicePoints(deviceId);
        return ResponseEntity.ok(ApiResponse.success("장비 포인트 목록", points));
    }

    @GetMapping("/points/mapping")
    public ResponseEntity<ApiResponse<DevicePointMappingPageResponse>> getDevicePointsForMapping(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean unmapped,
            @PageableDefault(size = 50, sort = {"device.deviceName", "tagName"}, direction = Sort.Direction.ASC) Pageable pageable
    ) {
        DevicePointMappingPageResponse result = deviceService.getDevicePointsForMappingPage(
                deviceId, categoryId, keyword, unmapped, pageable);
        return ResponseEntity.ok(ApiResponse.success("포인트 매핑 목록", result));
    }

    @PutMapping("/points/ref-mapping")
    public ResponseEntity<ApiResponse<Void>> updateDevicePointRefMappings(
            @Valid @RequestBody List<DevicePointRefMappingRequest> requests
    ) {
        deviceService.updateDevicePointRefMappings(requests);
        return ResponseEntity.ok(ApiResponse.success("포인트 SI 매핑 저장"));
    }

    //region swagger 🔄 포인트 매핑 엑셀 양식 다운로드 명세서
    @Operation(
            summary = "포인트 매핑 엑셀 양식 다운로드",
            description = """
                    현재 필터(category/keyword) 범위의 포인트 전체를 xlsx 양식으로 다운로드합니다.
                    헤더: 장비명 | 태그 | 포인트명 | point_key | ref_device_code | ref_point_code.
                    ref_* 컬럼에 SI 매핑값을 입력하여 업로드에 사용합니다. point_key 는 업로드 매칭 키이므로 수정하지 마세요.""",
            parameters = {
                    @Parameter(name = "categoryId", description = "카테고리 ID 필터", required = false),
                    @Parameter(name = "keyword", description = "검색 키워드", required = false)
            }
    )
    //endregion
    @GetMapping("/points/mapping/template")
    public ResponseEntity<byte[]> downloadPointMappingTemplate(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword
    ) {
        byte[] body = devicePointMappingExcelService.buildTemplate(categoryId, keyword);
        String filename = URLEncoder.encode("point-mapping-template.xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"point-mapping-template.xlsx\"; filename*=UTF-8''" + filename)
                .body(body);
    }

    //region swagger 🔄 포인트 매핑 엑셀 업로드 명세서
    @Operation(
            summary = "포인트 매핑 엑셀 업로드",
            description = """
                    엑셀(xlsx)로 SI 매핑을 일괄 반영합니다. point_key 로 포인트를 매칭하며
                    ref_device_code / ref_point_code 는 함께 입력하거나 모두 비워야 합니다.
                    유효한 행만 반영되고, 실패 행은 사유와 함께 결과에 포함됩니다(부분 반영)."""
    )
    //endregion
    @PostMapping(value = "/points/mapping/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PointMappingImportResult>> importPointMappings(
            @RequestParam("file") MultipartFile file
    ) {
        PointMappingImportResult result = devicePointMappingExcelService.importMappings(file);
        String message = String.format("총 %d건 중 %d건 반영, %d건 실패",
                result.total(), result.applied(), result.failed().size());
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    //region swagger 🔄 포인트 매핑 통계 조회 명세서
    @Operation(
            summary = "포인트 매핑 통계 조회",
            description = """
                    category/keyword 필터 범위 기준 전체 포인트 수(total)와 SI 매핑 완료 수(mapped)를 반환합니다.
                    미매핑 토글과 무관하게 범위 전체를 대상으로 산정합니다.""",
            parameters = {
                    @Parameter(name = "categoryId", description = "카테고리 ID 필터", required = false),
                    @Parameter(name = "keyword", description = "검색 키워드", required = false)
            }
    )
    //endregion
    @GetMapping("/points/mapping/stats")
    public ResponseEntity<ApiResponse<PointMappingStatsResponse>> getPointMappingStats(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword
    ) {
        PointMappingStatsResponse stats = devicePointMappingExcelService.getStats(categoryId, keyword);
        return ResponseEntity.ok(ApiResponse.success("포인트 매핑 통계", stats));
    }

    //region 🔄 장비 정보 수정 명세서
    @Operation(
            summary = "장비 정보 수정",
            description = "특정 장비 ID(deviceId)를 지정하고, 수정할 장비 정보 및 포인트 데이터를 Body에 담아 업데이트합니다.",
            parameters = {
                    @Parameter(name = "deviceId", description = "수정할 장비 ID", example = "100", required = true)
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "수정할 장비 및 포인트 데이터 정보",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = DeviceUpdateRequest.class),
                            examples = @ExampleObject(
                                    name = "장비 수정 예시",
                                    value = """
                                            {
                                              "categoryId": 12,
                                              "description" : "설명",
                                              "active" :true
                                            }
                                            """
                            )
                    )
            )
    )
    //endregion
    @PatchMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<DeviceResponse>> updateDevice(
            @PathVariable Long deviceId,
            @RequestBody DeviceUpdateRequest request) {
        DeviceResponse updated = deviceService.updateDevice(deviceId, request);
        return ResponseEntity.ok(ApiResponse.success("장비 정보 수정", updated));
    }

    //region swagger 🔄 장비 활성상태 일괄 변경 명세서
    @Operation(
            summary = "장비 활성상태 일괄 변경",
            description = """
                    장비 활성상태(active)를 전체/카테고리/개별 범위(scope)로 일괄 변경합니다.
                    - scope=ALL: 전체 장비 (categoryId/deviceIds 무시)
                    - scope=CATEGORY: 선택 카테고리(대·중·소) 하위 소분류 장비 (categoryId 필수)
                    - scope=DEVICES: 지정한 deviceIds 장비 (deviceIds 필수)
                    응답 data 는 변경된 장비 건수(updatedCount)입니다.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "일괄 변경 범위 및 활성상태 정보",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = BulkActiveRequest.class),
                            examples = @ExampleObject(
                                    name = "카테고리 단위 비활성화 예시",
                                    value = """
                                            {
                                              "scope": "CATEGORY",
                                              "categoryId": 12,
                                              "active": false
                                            }
                                            """
                            )
                    )
            )
    )
    //endregion
    @PatchMapping("/active")
    public ResponseEntity<ApiResponse<Integer>> updateActiveBulk(@RequestBody BulkActiveRequest request) {
        int updated = deviceService.updateActiveBulk(request);
        return ResponseEntity.ok(ApiResponse.success(updated + "건 활성상태 변경", updated));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(@PathVariable Long deviceId) {
        deviceService.deleteDevice(deviceId);
        return ResponseEntity.ok(ApiResponse.success("장비 삭제"));
    }

    //region swagger 🔄 층 및 구역별 장비 목록 조회 명세서
    @Operation(
            summary = "층, 구역 별 장비 목록 조회",
            description = "선택된 층(floor) 또는 구역(zone) 필터를 기반으로 해당하는 장비 목록을 조회합니다.",
            parameters = {
                    @Parameter(name = "floor", description = "층 ID (예: 1층, 2층 등)", example = "1", required = false),
                    @Parameter(name = "zone", description = "구역 ID (예: A구역, B구역 등)", example = "5", required = false)
            }
    )
    //endregion
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getSearchDevices(
            @RequestParam(required = false) Long floor,
            @RequestParam(required = false) Long zone) {
        List<DeviceResponse> devices = deviceService.getSearchDevices(floor, zone);
        return ResponseEntity.ok(ApiResponse.success("층, 구역 별 장비 목록 조회", devices));
    }

    //region swagger 🔄 장비 배치정보 단일 저장 명세서
    @Operation(
            summary = "장비 배치정보 단일 저장",
            description = "여러 장비의 배치정보(위치/회전/스케일 등)를 단일 트랜잭션으로 저장/수정합니다."
    )
    //endregion
    @PutMapping("/placement")
    public ResponseEntity<ApiResponse<DevicePlacementResponse>> saveDevicesPlacement(
            @Valid @RequestBody DevicePlacementRequest request) {
        DevicePlacementResponse response = devicePlacementService.save(request);
        return ResponseEntity.ok(ApiResponse.success("장비 배치정보 업데이트", response));
    }

    //region swagger 🔄 장비 배치정보 일괄 저장 명세서
    @Operation(
            summary = "장비 배치정보 일괄 저장",
            description = "여러 장비의 배치정보(위치/회전/스케일 등)를 단일 트랜잭션으로 일괄 저장/수정합니다."
    )
    //endregion
    @PutMapping("/placement/bulk")
    public ResponseEntity<ApiResponse<List<DevicePlacementResponse>>> saveDevicesPlacementBulk(
            @RequestBody @Valid @NotEmpty List<@Valid DevicePlacementRequest> requests) {
        log.info("장비 배치 일괄 저장: count={}", requests.size());
        List<DevicePlacementResponse> responses = devicePlacementService.saveAll(requests);
        return ResponseEntity.ok(ApiResponse.success("장비 배치정보 일괄 업데이트", responses));
    }

    @DeleteMapping("/placement")
    public ResponseEntity<ApiResponse<Void>> deleteDevicesPlacement(
            @RequestBody Map<String, List<Long>> request) {
        List<Long> deviceIds = request.get("deviceIds");
        devicePlacementService.deleteByDeviceIds(deviceIds);
        return ResponseEntity.ok(ApiResponse.success("장비 배치정보 삭제"));
    }


    @PutMapping("/{deviceId}/points/tag-mapping")
    public ResponseEntity<ApiResponse<Void>> saveTagMapping(
            @PathVariable Long deviceId,
            @RequestBody Map<String, String> mappings
    ) {
        deviceService.saveTagMappingsByDevice(deviceId, mappings);
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

    @GetMapping("/cctv-candidates")
    public ResponseEntity<ApiResponse<List<CctvInfoProjection>>> getCctvCandidates() {
        List<CctvInfoProjection> list = deviceService.getCctvCandidates();
        return ResponseEntity.ok(ApiResponse.success("CCTV 매핑 후보 목록", list));
    }

    @GetMapping("/property-types")
    public ResponseEntity<ApiResponse<List<DevicePropertyTypeResponse>>> getPropertyTypes() {
        return ResponseEntity.ok(ApiResponse.success("장비 property 타입 메타", deviceService.getPropertyTypes()));
    }
}
