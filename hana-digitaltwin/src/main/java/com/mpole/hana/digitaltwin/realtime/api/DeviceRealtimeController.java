package com.mpole.hana.digitaltwin.realtime.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.realtime.dto.DeviceRealtimeRequest;
import com.mpole.hana.digitaltwin.realtime.dto.DeviceRealtimeResponse;
import com.mpole.hana.digitaltwin.realtime.service.DeviceRealtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Realtime Device", description = "실시간 장비 API")
public class DeviceRealtimeController {
    private final DeviceRealtimeService deviceRealtimeService;

    //region 🔄 실시간 장비 상태 조회 명세서
    @Operation(
            summary = "실시간 장비 상태 조회",
            description = "여러 장비의 ID 배열을 받아 각각의 실시간 상태 정보를 조회합니다.",
            security = @SecurityRequirement(name = "bearerAuth"),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "조회할 장비 ID 리스트",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = DeviceRealtimeRequest.class))
            )
    )
    //endregion
    @PostMapping
    public ResponseEntity<ApiResponse<List<DeviceRealtimeResponse>>> getDeviceStates(
            @Valid @RequestBody DeviceRealtimeRequest request
    ) {
        List<DeviceRealtimeResponse> response = deviceRealtimeService.getDeviceStates(request.deviceIds());
        return ResponseEntity.ok(ApiResponse.success("실시간 장비 상태 조회", response));
    }
}
