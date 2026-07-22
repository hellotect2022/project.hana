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

    //region swagger 🔄 시스템/하위 시스템별 장비 목록 조회 명세서
    @Operation(
            summary = "시스템/하위 시스템별 장비 목록 조회",
            description = "subSystemId(3단) 또는 systemId(직접 매핑, 2단) 중 하나로 소분류 카테고리에 속한 장비 목록을 조회합니다. "
                    + "subSystemId 가 있으면 subSystem 매핑 기준, 없으면 systemId 직접 매핑(deviceSubSystem==null) 기준으로 조회합니다. "
                    + "둘 다 없으면 400 을 반환합니다.",
            parameters = {
                    @Parameter(name = "subSystemId", description = "하위 시스템 ID (3단 경로)", example = "1"),
                    @Parameter(name = "systemId", description = "시스템 ID (직접 매핑 2단 경로)", example = "1"),
                    @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", example = "0"),
                    @Parameter(name = "size", description = "페이지 당 데이터 개수", example = "20"),
            }
    )
    //endregion
    @GetMapping("/device-system/devices")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<DeviceResponse>>> getDevicesBySystemOrSubSystem(
            @RequestParam(name = "subSystemId", required = false) Long subSystemId,
            @RequestParam(name = "systemId", required = false) Long systemId,
            @Parameter(hidden = true) @PageableDefault(size = 20) Pageable pageable

    ) {
        if (subSystemId != null) {
            Page<DeviceResponse> devices = deviceService.getDevicesBySubSystemId(subSystemId, pageable);
            return ResponseEntity.ok(ApiResponse.success("하위 시스템별 장비 목록 조회", devices));
        }
        if (systemId != null) {
            Page<DeviceResponse> devices = deviceService.getDevicesBySystemId(systemId, pageable);
            return ResponseEntity.ok(ApiResponse.success("시스템 직접 매핑 장비 목록 조회", devices));
        }
        throw new IllegalArgumentException("subSystemId 또는 systemId 중 하나는 필수입니다.");
    }

}
