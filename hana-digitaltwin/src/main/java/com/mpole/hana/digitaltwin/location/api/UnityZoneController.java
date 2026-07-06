package com.mpole.hana.digitaltwin.location.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityFloorOptionResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityZoneAdminResponse;
import com.mpole.hana.digitaltwin.location.dto.UnityZoneRequest;
import com.mpole.hana.digitaltwin.location.service.UnityZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 콜라이더(존) 관리 API */
@RestController
@RequestMapping("/api/unity-zones")
@RequiredArgsConstructor
public class UnityZoneController {

    private final UnityZoneService unityZoneService;

    /** 층 선택용 옵션 목록 */
    @GetMapping("/floors")
    public ResponseEntity<ApiResponse<List<UnityFloorOptionResponse>>> getFloorOptions() {
        return ResponseEntity.ok(ApiResponse.success("층 목록 조회", unityZoneService.getFloorOptions()));
    }

    /** 콜라이더 목록 (floorId 로 층별 필터) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UnityZoneAdminResponse>>> getZones(
            @RequestParam(required = false) Long floorId) {
        return ResponseEntity.ok(ApiResponse.success("콜라이더 목록 조회", unityZoneService.getZones(floorId)));
    }

    /** 콜라이더 단건 조회 */
    @GetMapping("/{zoneId}")
    public ResponseEntity<ApiResponse<UnityZoneAdminResponse>> getZone(@PathVariable Long zoneId) {
        return ResponseEntity.ok(ApiResponse.success("콜라이더 조회", unityZoneService.getZone(zoneId)));
    }

    /** 콜라이더 등록 */
    @PostMapping
    public ResponseEntity<ApiResponse<UnityZoneAdminResponse>> createZone(
            @Valid @RequestBody UnityZoneRequest request) {
        return ResponseEntity.ok(ApiResponse.success("콜라이더 등록", unityZoneService.createZone(request)));
    }

    /** 콜라이더 수정 */
    @PutMapping("/{zoneId}")
    public ResponseEntity<ApiResponse<UnityZoneAdminResponse>> updateZone(
            @PathVariable Long zoneId,
            @Valid @RequestBody UnityZoneRequest request) {
        return ResponseEntity.ok(ApiResponse.success("콜라이더 수정", unityZoneService.updateZone(zoneId, request)));
    }
}
