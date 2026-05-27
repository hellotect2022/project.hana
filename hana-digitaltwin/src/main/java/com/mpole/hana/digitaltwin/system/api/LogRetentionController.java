package com.mpole.hana.digitaltwin.system.api;

import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.system.dto.LogRetentionBatchUpdateRequest;
import com.mpole.hana.digitaltwin.system.dto.LogRetentionPolicyResponse;
import com.mpole.hana.digitaltwin.system.dto.LogRetentionPurgeResponse;
import com.mpole.hana.digitaltwin.system.service.LogRetentionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/log-retention")
@RequiredArgsConstructor
public class LogRetentionController {

    private final LogRetentionService logRetentionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LogRetentionPolicyResponse>>> getPolicies() {
        return ResponseEntity.ok(
                ApiResponse.success("로그 보관 정책", logRetentionService.getPolicies())
        );
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> updatePolicies(
            @Valid @RequestBody LogRetentionBatchUpdateRequest request
    ) {
        logRetentionService.updatePolicies(request);
        return ResponseEntity.ok(ApiResponse.success("보관 정책이 저장되었습니다"));
    }

    @PostMapping("/run-purge")
    public ResponseEntity<ApiResponse<LogRetentionPurgeResponse>> runPurge() {
        return ResponseEntity.ok(
                ApiResponse.success("만료 로그 정리 완료", logRetentionService.purgeExpiredLogs())
        );
    }
}
