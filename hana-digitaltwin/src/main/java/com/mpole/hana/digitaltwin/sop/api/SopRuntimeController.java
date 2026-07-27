package com.mpole.hana.digitaltwin.sop.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopInstanceDtos.CreateRequest;
import com.mpole.hana.digitaltwin.sop.dto.SopInstanceDtos.InstanceResponse;
import com.mpole.hana.digitaltwin.sop.dto.SopInstanceDtos.SaveResultRequest;
import com.mpole.hana.digitaltwin.sop.service.SopRuntimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * SOP 런타임(실행 인스턴스) API.
 */
@RestController
@RequestMapping("/api/sop/instances")
@RequiredArgsConstructor
public class SopRuntimeController {

    private final SopRuntimeService service;

    /** 인스턴스 생성(스냅샷 고정). */
    @PostMapping
    public ResponseEntity<ApiResponse<InstanceResponse>> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SOP 인스턴스 생성", service.createInstance(request)));
    }

    /** 인스턴스 조회(스냅샷 + 결과). */
    @GetMapping("/{instanceId}")
    public ResponseEntity<ApiResponse<InstanceResponse>> get(@PathVariable Long instanceId) {
        return ResponseEntity.ok(ApiResponse.success("SOP 인스턴스 조회", service.getInstance(instanceId)));
    }

    /** 결과 저장(방식 B — 실행 결과 한 번에). */
    @PutMapping("/{instanceId}/result")
    public ResponseEntity<ApiResponse<InstanceResponse>> saveResult(
            @PathVariable Long instanceId, @RequestBody SaveResultRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SOP 결과 저장", service.saveResult(instanceId, request)));
    }
}
