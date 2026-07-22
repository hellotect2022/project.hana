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
