package com.mpole.hana.digitaltwin.command.api;

import com.mpole.hana.digitaltwin.command.dto.CommandPointRequest;
import com.mpole.hana.digitaltwin.command.dto.CommandPointResponse;
import com.mpole.hana.digitaltwin.command.service.CommandPointService;
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
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/command/points")
@RequiredArgsConstructor
@Tag(name = "CommandPoint", description = "디바이스 제어 포인트 관리 API")
public class CommandPointController {

    private final CommandPointService commandPointService;

    @Operation(
            summary = "제어 포인트 목록 조회",
            description = "특정 디바이스에 등록된 제어 포인트를 정렬순으로 조회합니다.",
            parameters = {@Parameter(name = "deviceId", description = "디바이스 ID", example = "1", required = true)}
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<CommandPointResponse>>> list(@RequestParam Long deviceId) {
        List<CommandPointResponse> points = commandPointService.listByDevice(deviceId);
        return ResponseEntity.ok(ApiResponse.success("제어 포인트 목록 조회", points));
    }

    @Operation(
            summary = "제어 포인트 전체 페이지 조회",
            description = "장비 선택 없이 제어 포인트를 페이징 조회합니다. keyword(장비명/tagName/라벨)·categoryId(소분류) 선택 필터.",
            parameters = {
                    @Parameter(name = "keyword", description = "장비명/tagName/라벨 부분검색"),
                    @Parameter(name = "categoryId", description = "장비 소분류 카테고리 ID"),
                    @Parameter(name = "page", description = "페이지(0-base)", example = "0"),
                    @Parameter(name = "size", description = "페이지 크기", example = "20")
            }
    )
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<ApiResponse.PageResponse<CommandPointResponse>>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @Parameter(hidden = true)
            //@PageableDefault(size = 20, sort = "commandPointId", direction = Sort.Direction.DESC) Pageable pageable
            @PageableDefault(size = 20)
            @SortDefault.SortDefaults({
            @SortDefault(sort = "device.deviceName", direction = Sort.Direction.ASC),
            @SortDefault(sort = "sortOrder",         direction = Sort.Direction.ASC),
            @SortDefault(sort = "commandPointId",    direction = Sort.Direction.ASC)
}) Pageable pageable
    ) {
        Page<CommandPointResponse> result = commandPointService.page(keyword, categoryId, pageable);
        return ResponseEntity.ok(ApiResponse.success("제어 포인트 페이지 조회", result));
    }

    @Operation(summary = "제어 포인트 등록", description = "디바이스의 포인트를 제어 포인트로 등록합니다. 포인트는 해당 디바이스 소속이어야 합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<CommandPointResponse>> create(@RequestBody CommandPointRequest request) {
        CommandPointResponse created = commandPointService.create(request);
        return ResponseEntity.ok(ApiResponse.success("제어 포인트 등록", created));
    }

    @Operation(
            summary = "제어 포인트 수정",
            description = "제어 포인트 ID의 항목을 수정합니다.",
            parameters = {@Parameter(name = "id", description = "제어 포인트 ID", example = "1", required = true)}
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CommandPointResponse>> update(
            @PathVariable Long id,
            @RequestBody CommandPointRequest request) {
        CommandPointResponse updated = commandPointService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("제어 포인트 수정", updated));
    }

    @Operation(
            summary = "제어 포인트 삭제",
            description = "제어 포인트 ID의 항목을 삭제합니다.",
            parameters = {@Parameter(name = "id", description = "제어 포인트 ID", example = "1", required = true)}
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        commandPointService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("제어 포인트 삭제"));
    }
}
