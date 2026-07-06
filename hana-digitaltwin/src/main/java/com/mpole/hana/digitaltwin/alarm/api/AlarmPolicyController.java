package com.mpole.hana.digitaltwin.alarm.api;

import com.mpole.hana.digitaltwin.alarm.dto.AlarmPolicyRequest;
import com.mpole.hana.digitaltwin.alarm.dto.AlarmPolicyResponse;
import com.mpole.hana.digitaltwin.alarm.service.AlarmPolicyService;
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
@RequestMapping("/api/alarm/policies")
@RequiredArgsConstructor
@Tag(name = "AlarmPolicy", description = "알람 정책 관리 API")
public class AlarmPolicyController {

    private final AlarmPolicyService alarmPolicyService;

    @Operation(summary = "알람 정책 등록", description = "카테고리/장비 범위의 임계치(THRESHOLD) 또는 토글(TOGGLE) 알람 정책과 심각도 레벨을 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<AlarmPolicyResponse>> create(@RequestBody AlarmPolicyRequest request) {
        AlarmPolicyResponse created = alarmPolicyService.create(request);
        return ResponseEntity.ok(ApiResponse.success("알람 정책 등록", created));
    }

    @Operation(summary = "알람 정책 목록 조회", description = "등록된 알람 정책 전체를 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AlarmPolicyResponse>>> list() {
        List<AlarmPolicyResponse> policies = alarmPolicyService.list();
        return ResponseEntity.ok(ApiResponse.success("알람 정책 목록 조회", policies));
    }

    @Operation(
            summary = "알람 정책 상세 조회",
            description = "정책 ID로 단일 알람 정책과 레벨 상세를 조회합니다.",
            parameters = {@Parameter(name = "id", description = "알람 정책 ID", example = "1", required = true)}
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AlarmPolicyResponse>> get(@PathVariable Long id) {
        AlarmPolicyResponse policy = alarmPolicyService.get(id);
        return ResponseEntity.ok(ApiResponse.success("알람 정책 상세 조회", policy));
    }

    @Operation(
            summary = "알람 정책 수정",
            description = "정책 ID의 알람 정책을 수정합니다. 레벨 목록은 전체 교체됩니다.",
            parameters = {@Parameter(name = "id", description = "알람 정책 ID", example = "1", required = true)}
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AlarmPolicyResponse>> update(
            @PathVariable Long id,
            @RequestBody AlarmPolicyRequest request) {
        AlarmPolicyResponse updated = alarmPolicyService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("알람 정책 수정", updated));
    }

    @Operation(
            summary = "알람 정책 삭제",
            description = "정책 ID의 알람 정책과 하위 레벨을 삭제합니다.",
            parameters = {@Parameter(name = "id", description = "알람 정책 ID", example = "1", required = true)}
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        alarmPolicyService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("알람 정책 삭제"));
    }
}
