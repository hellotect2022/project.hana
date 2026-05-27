package com.mpole.hana.digitaltwin.iam.auth.api;

import com.mpole.hana.digitaltwin.audit.service.AuditLogService;
import com.mpole.hana.digitaltwin.global.response.ApiResponse;
import com.mpole.hana.digitaltwin.global.security.JwtAuthenticationFilter;
import com.mpole.hana.digitaltwin.iam.auth.dto.ChangePasswordRequest;
import com.mpole.hana.digitaltwin.iam.auth.dto.LoginRequest;
import com.mpole.hana.digitaltwin.iam.auth.dto.LoginResponse;
import com.mpole.hana.digitaltwin.iam.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuditLogService auditLogService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        try {
            return ResponseEntity.ok(
                    ApiResponse.success("로그인 성공", authService.login(request, httpRequest))
            );
        }catch (Exception e) {
            auditLogService.recordLoginFailure(request.loginId(), httpRequest, e.getMessage());
            throw e;
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "토큰 갱신 성공",
                        authService.refreshToken(principal.loginId(), principal.deviceId(), httpRequest)
                )
        );
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(principal.loginId(), request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호 변경 완료"));
    }

    @PostMapping("/unlock/{loginId}")
    public ResponseEntity<ApiResponse<Void>> unlockAccount(@PathVariable String loginId) {
        authService.unlockAccount(loginId);
        return ResponseEntity.ok(ApiResponse.success("계정 잠금 해제 완료"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginResponse.UserInfo>> me(
            @AuthenticationPrincipal JwtAuthenticationFilter.UserPrincipal principal
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("현재 사용자", authService.getUserInfo(principal.loginId()))
        );
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth Service is running");
    }

}
