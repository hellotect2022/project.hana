package com.mpole.hana.digitaltwin.device.api;

import com.mpole.hana.digitaltwin.device.dto.DeviceResponse;
import com.mpole.hana.digitaltwin.device.service.DeviceService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
@Tag(name = "Device By System", description = "시스템별 장비 조회 API")
public class DeviceBySystemController {

    private final DeviceService deviceService;

    //region swagger 🔄 시스템별 장비 목록 조회 명세서
    @Operation(
            summary = "시스템별 장비 목록 조회",
            description = "BMS system_id에 매핑된 소분류 카테고리에 속한 전체 장비 목록을 조회합니다.",
            parameters = {
                    @Parameter(name = "systemId", description = "BMS 시스템 ID", example = "1", required = true),
                    @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", example = "0"),
                    @Parameter(name = "size", description = "페이지 당 데이터 개수", example = "20"),
            }
    )
    //endregion
    @GetMapping("/device-system/devices")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<DeviceResponse>>> getDevicesBySystemId(
            @RequestParam(name = "systemId") Long systemId,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable

    ) {
        Page<DeviceResponse> devices = deviceService.getDevicesBySystemId(systemId,pageable);
        return ResponseEntity.ok(ApiResponse.success("시스템별 장비 목록 조회", devices));
    }

}
