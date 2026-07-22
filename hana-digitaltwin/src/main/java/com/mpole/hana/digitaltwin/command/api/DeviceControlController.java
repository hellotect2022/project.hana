package com.mpole.hana.digitaltwin.command.api;

import com.mpole.hana.digitaltwin.command.dto.DeviceControlRequest;
import com.mpole.hana.digitaltwin.command.dto.DeviceControlResponse;
import com.mpole.hana.digitaltwin.command.service.DeviceControlService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/command/control")
@RequiredArgsConstructor
@Tag(name = "DeviceControl", description = "디바이스 제어 명령 API")
public class DeviceControlController {

    private final DeviceControlService deviceControlService;

    @Operation(summary = "디바이스 제어", description = "제어 포인트(commandPointId)에 on/off 명령을 전송해 외부(XN) 연계로 중계합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<DeviceControlResponse>> control(@RequestBody DeviceControlRequest request) {
        DeviceControlResponse result = deviceControlService.control(request);
        return ResponseEntity.ok(ApiResponse.success("디바이스 제어 명령", result));
    }
}
