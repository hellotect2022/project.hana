package com.mpole.hana.digitaltwin.buildingLighting.api;

import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingControlRequest;
import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingControlResultResponse;
import com.mpole.hana.digitaltwin.buildingLighting.dto.LightingDimmingRequest;
import com.mpole.hana.digitaltwin.buildingLighting.service.BuildingLightingControlService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 건물조명 제어 API — 전 구역/특정 층/특정 구역 점·소등 및 구역 디밍 조절.
 * <p>제어 명령은 연계(SI) 서비스로 전달된다(현재는 스텁). 대상 장비를 식별해 결과를 반환한다.
 */
@RestController
@RequestMapping("/api/building-lighting/control")
@RequiredArgsConstructor
@Tag(name = "Building Lighting Control", description = "건물조명 점/소등·디밍 제어 API")
public class BuildingLightingControlController {

    private final BuildingLightingControlService controlService;

    @Operation(
            summary = "전 구역 점/소등",
            description = "건물조명 전체에 대해 점등(ON) 또는 소등(OFF) 명령을 전송합니다."
    )
    @PostMapping("/all")
    public ResponseEntity<ApiResponse<LightingControlResultResponse>> controlAll(
            @Valid @RequestBody LightingControlRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "전 구역 점/소등 명령 전송", controlService.controlAll(request.command())));
    }

    @Operation(
            summary = "특정 층 점/소등",
            description = "지정한 층(floorId)의 조명을 점등/소등합니다.",
            parameters = @Parameter(name = "floorId", description = "Unity 층 ID", example = "1", required = true)
    )
    @PostMapping("/floors/{floorId}")
    public ResponseEntity<ApiResponse<LightingControlResultResponse>> controlFloor(
            @PathVariable Long floorId,
            @Valid @RequestBody LightingControlRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "특정 층 점/소등 명령 전송", controlService.controlFloor(floorId, request.command())));
    }

    @Operation(
            summary = "특정 구역 점/소등",
            description = "지정한 구역(zoneId)의 조명을 점등/소등합니다.",
            parameters = @Parameter(name = "zoneId", description = "Unity 구역 ID", example = "1", required = true)
    )
    @PostMapping("/zones/{zoneId}")
    public ResponseEntity<ApiResponse<LightingControlResultResponse>> controlZone(
            @PathVariable Long zoneId,
            @Valid @RequestBody LightingControlRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "특정 구역 점/소등 명령 전송", controlService.controlZone(zoneId, request.command())));
    }

    @Operation(
            summary = "특정 구역 디밍 조절",
            description = "지정한 구역(zoneId)의 조명 밝기를 0~100 으로 조절합니다.",
            parameters = @Parameter(name = "zoneId", description = "Unity 구역 ID", example = "1", required = true)
    )
    @PostMapping("/zones/{zoneId}/dimming")
    public ResponseEntity<ApiResponse<LightingControlResultResponse>> dimZone(
            @PathVariable Long zoneId,
            @Valid @RequestBody LightingDimmingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "특정 구역 디밍 조절 명령 전송", controlService.dimZone(zoneId, request.level())));
    }
}
