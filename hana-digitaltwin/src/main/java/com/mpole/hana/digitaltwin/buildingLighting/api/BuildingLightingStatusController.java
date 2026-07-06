package com.mpole.hana.digitaltwin.buildingLighting.api;

import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingFloorStatusResponse;
import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingOperationStatusResponse;
import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingZoneStatusResponse;
import com.mpole.hana.digitaltwin.buildingLighting.service.BuildingLightingStatusService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 건물조명(LCS, systemId=3) 조회 API. UI 우측 패널의 운용현황/구역별/층별 표출 데이터를 제공한다.
 */
@RestController
@RequestMapping("/api/building-lighting")
@RequiredArgsConstructor
@Tag(name = "Building Lighting Status", description = "건물조명 운용현황/구역별/층별 조회 API")
public class BuildingLightingStatusController {

    private final BuildingLightingStatusService statusService;

    @Operation(
            summary = "건물조명 운용현황 조회",
            description = "건물조명 전체 집계 — Total / 점등(ON) / 소등(OFF) / 운영 / 통신정상 / 통신비정상 / 알람 개수."
    )
    @GetMapping("/operation-status")
    public ResponseEntity<ApiResponse<LightingOperationStatusResponse>> getOperationStatus() {
        return ResponseEntity.ok(ApiResponse.success(
                "건물조명 운용현황 조회", statusService.getOperationStatus()));
    }

    @Operation(
            summary = "건물조명 구역별 현황 조회(전경)",
            description = "전경 화면용 — 전체 구역(zone)별 점/소등 상태, 평균 디밍, 통신상태, 장비 수."
    )
    @GetMapping("/zones")
    public ResponseEntity<ApiResponse<List<LightingZoneStatusResponse>>> getZoneStatuses() {
        return ResponseEntity.ok(ApiResponse.success(
                "건물조명 구역별 현황 조회", statusService.getZoneStatuses()));
    }

    @Operation(
            summary = "건물조명 층별 현황 조회",
            description = "층 선택 패널용 — 층(floor)별 점/소등 요약(장비 수, 구역 수, ON/OFF 개수, 대표 상태)."
    )
    @GetMapping("/floors")
    public ResponseEntity<ApiResponse<List<LightingFloorStatusResponse>>> getFloorStatuses() {
        return ResponseEntity.ok(ApiResponse.success(
                "건물조명 층별 현황 조회", statusService.getFloorStatuses()));
    }

    @Operation(
            summary = "특정 층 구역별 현황 조회",
            description = "층 선택 시 — 해당 층(floorId)에 속한 구역(zone)별 현황.",
            parameters = @Parameter(name = "floorId", description = "Unity 층 ID", example = "1", required = true)
    )
    @GetMapping("/floors/{floorId}/zones")
    public ResponseEntity<ApiResponse<List<LightingZoneStatusResponse>>> getZoneStatusesByFloor(
            @PathVariable Long floorId) {
        return ResponseEntity.ok(ApiResponse.success(
                "특정 층 구역별 현황 조회", statusService.getZoneStatusesByFloor(floorId)));
    }
}
