package com.mpole.hana.digitaltwin.realtime.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.realtime.dto.DeviceRealtimeRequest;
import com.mpole.hana.digitaltwin.realtime.dto.DeviceRealtimeResponse;
import com.mpole.hana.digitaltwin.realtime.service.DeviceRealtimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/device/rt")
@RequiredArgsConstructor
public class DeviceRealtimeController {
    private final DeviceRealtimeService deviceRealtimeService;

    @PostMapping
    public ResponseEntity<ApiResponse<List<DeviceRealtimeResponse>>> getDeviceStates(
            @Valid @RequestBody DeviceRealtimeRequest request
    ) {
        List<DeviceRealtimeResponse> response = deviceRealtimeService.getDeviceStates(request.deviceIds());
        return ResponseEntity.ok(ApiResponse.success("실시간 장비 상태 조회", response));
    }
}
