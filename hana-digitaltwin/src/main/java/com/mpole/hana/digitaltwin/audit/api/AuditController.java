package com.mpole.hana.digitaltwin.audit.api;

import com.mpole.hana.digitaltwin.audit.dto.*;
import com.mpole.hana.digitaltwin.audit.service.AuditLogService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.global.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogService auditLogService;

    @PostMapping("/usage")
    public ResponseEntity<ApiResponse<Void>> postUsage(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @Valid @RequestBody UsageLogCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        auditLogService.recordUsage(principal.loginId(), request, httpRequest);
        return ResponseEntity.ok(ApiResponse.success("사용 로그 저장"));
    }

    @GetMapping("/access-logs")
    public ResponseEntity<ApiResponse<PageAuditResponse<AccessLogItemResponse>>> getAccessLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("접속 로그", auditLogService.getAccessLogs(from, to, page, size))
        );
    }

    @GetMapping("/usage-logs")
    public ResponseEntity<ApiResponse<PageAuditResponse<UsageLogItemResponse>>> getUsageLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("사용 로그", auditLogService.getUsageLogs(from, to, page, size))
        );
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<AuditStatisticsResponse>> getStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("운영 통계", auditLogService.getStatistics(from, to))
        );
    }
}
