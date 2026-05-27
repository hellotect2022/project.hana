package com.mpole.hana.digitaltwin.systemMenu.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.systemMenu.dto.DeviceSystemCreateRequest;
import com.mpole.hana.digitaltwin.systemMenu.dto.DeviceSystemResponse;
import com.mpole.hana.digitaltwin.systemMenu.dto.DeviceSystemSortOrderRequest;
import com.mpole.hana.digitaltwin.systemMenu.dto.DeviceSystemUpdateRequest;
import com.mpole.hana.digitaltwin.systemMenu.service.DeviceSystemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device-system")
@RequiredArgsConstructor
public class DeviceSystemController {

    private final DeviceSystemService deviceSystemService;

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<DeviceSystemResponse>>> getDeviceSystems() {
        return ResponseEntity.ok(
                ApiResponse.success("BMS 시스템 목록 조회", deviceSystemService.getDeviceSystems())
        );
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> createDeviceSystem(
            @Valid @RequestBody DeviceSystemCreateRequest request
    ) {
        deviceSystemService.createDeviceSystem(request);
        return ResponseEntity.ok(ApiResponse.success("BMS 시스템 생성"));
    }

    @PutMapping("/{systemId}")
    public ResponseEntity<ApiResponse<Void>> updateDeviceSystem(
            @PathVariable Long systemId,
            @Valid @RequestBody DeviceSystemUpdateRequest request
    ) {
        deviceSystemService.updateDeviceSystem(systemId, request);
        return ResponseEntity.ok(ApiResponse.success("BMS 시스템 정보 수정"));
    }

    @PutMapping("/sort-orders")
    public ResponseEntity<ApiResponse<Void>> updateDeviceSystemSortOrders(
            @Valid @RequestBody List<DeviceSystemSortOrderRequest> requests
    ) {
        deviceSystemService.updateDeviceSystemSortOrders(requests);
        return ResponseEntity.ok(ApiResponse.success("BMS 시스템 순서 변경 저장"));
    }

    @DeleteMapping("/{systemId}")
    public ResponseEntity<ApiResponse<Void>> deleteDeviceSystem(
            @PathVariable Long systemId
    ) {
        deviceSystemService.deleteDeviceSystem(systemId);
        return ResponseEntity.ok(ApiResponse.success("BMS 시스템 삭제"));
    }
}
