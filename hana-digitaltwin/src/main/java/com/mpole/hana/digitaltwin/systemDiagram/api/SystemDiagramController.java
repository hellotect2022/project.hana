package com.mpole.hana.digitaltwin.systemDiagram.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.systemDiagram.dto.SystemDiagramRequest;
import com.mpole.hana.digitaltwin.systemDiagram.dto.SystemDiagramResponse;
import com.mpole.hana.digitaltwin.systemDiagram.service.SystemDiagramService;
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
@RequestMapping("/api/system-diagram")
@RequiredArgsConstructor
@Tag(name = "SystemDiagram", description = "계통도 관리 API")
public class SystemDiagramController {

    private final SystemDiagramService systemDiagramService;

    @Operation(summary = "계통도 등록", description = "마스터 장비를 중심으로 소속 서브 장비와 배선(배관 세그먼트 포함)을 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<SystemDiagramResponse>> create(@RequestBody SystemDiagramRequest request) {
        SystemDiagramResponse created = systemDiagramService.create(request);
        return ResponseEntity.ok(ApiResponse.success("계통도 등록", created));
    }

    @Operation(summary = "계통도 목록 조회", description = "등록된 계통도 전체를 정렬순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemDiagramResponse>>> list() {
        List<SystemDiagramResponse> diagrams = systemDiagramService.list();
        return ResponseEntity.ok(ApiResponse.success("계통도 목록 조회", diagrams));
    }

    @Operation(
            summary = "계통도 상세 조회",
            description = "계통도 ID로 단일 계통도와 하위 장비/배선 상세를 조회합니다.",
            parameters = {@Parameter(name = "id", description = "계통도 ID", example = "1", required = true)}
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SystemDiagramResponse>> get(@PathVariable Long id) {
        SystemDiagramResponse diagram = systemDiagramService.get(id);
        return ResponseEntity.ok(ApiResponse.success("계통도 상세 조회", diagram));
    }

    @Operation(
            summary = "계통도 수정",
            description = "계통도 ID의 계통도를 수정합니다. 하위 장비/배선 목록은 전체 교체됩니다.",
            parameters = {@Parameter(name = "id", description = "계통도 ID", example = "1", required = true)}
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SystemDiagramResponse>> update(
            @PathVariable Long id,
            @RequestBody SystemDiagramRequest request) {
        SystemDiagramResponse updated = systemDiagramService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("계통도 수정", updated));
    }

    @Operation(
            summary = "계통도 삭제",
            description = "계통도 ID의 계통도와 하위 장비/배선을 삭제합니다.",
            parameters = {@Parameter(name = "id", description = "계통도 ID", example = "1", required = true)}
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        systemDiagramService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("계통도 삭제"));
    }
}
