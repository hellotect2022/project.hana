package com.mpole.hana.digitaltwin.device.api;

import com.mpole.hana.digitaltwin.device.dto.DeviceResponse;
import com.mpole.hana.digitaltwin.device.service.DeviceService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceBySystemController {

    private final DeviceService deviceService;

    /**
     * BMS system_id에 매핑된 소분류 카테고리에 속한 장비 목록 조회
     */
    @GetMapping("/device-system/devices")
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getDevicesBySystemId(
            @RequestParam(name = "systemId") Long systemId
    ) {
        List<DeviceResponse> devices = deviceService.getDevicesBySystemId(systemId);
        return ResponseEntity.ok(ApiResponse.success("시스템별 장비 목록 조회", devices));
    }

}
