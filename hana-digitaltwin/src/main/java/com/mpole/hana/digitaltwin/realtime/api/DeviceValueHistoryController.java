package com.mpole.hana.digitaltwin.realtime.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.realtime.dto.DeviceValueHistoryResponse;
import com.mpole.hana.digitaltwin.realtime.service.DeviceValueHistoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 디바이스 실시간 값 이력 조회 API(읽기 전용). 쓰기는 event-server(R2DBC append).
 */
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Tag(name = "DeviceValueHistory", description = "디바이스 실시간 값 이력 조회 API")
public class DeviceValueHistoryController {

    private final DeviceValueHistoryQueryService service;

    @Operation(
            summary = "장비 실시간 값 이력 조회",
            description = "device 별 실시간 값 이력을 created_at 최신순으로 기간(from~to, 선택) 페이징 조회합니다.",
            parameters = {
                    @Parameter(name = "deviceId", description = "장비 ID", example = "1001", required = true),
                    @Parameter(name = "from", description = "시작(ISO-8601)"),
                    @Parameter(name = "to", description = "종료(ISO-8601)"),
                    @Parameter(name = "page", description = "페이지(0-base)", example = "0"),
                    @Parameter(name = "size", description = "페이지 크기", example = "50")
            }
    )
    @GetMapping("/{deviceId}/value-history")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<DeviceValueHistoryResponse>>> history(
            @PathVariable Long deviceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(hidden = true)
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<DeviceValueHistoryResponse> result = service.getHistory(deviceId, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success("장비 실시간 값 이력 조회", result));
    }
}
